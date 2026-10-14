package bf.fasoguardian.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.identite.LiensTutelle;
import bf.fasoguardian.identite.SecondFacteur;
import bf.fasoguardian.identite.SecondFacteur.ActionSensible;
import bf.fasoguardian.identite.domaine.DossierKyc;
import bf.fasoguardian.identite.domaine.InvitationTuteur;
import bf.fasoguardian.identite.domaine.InvitationTuteur.Statut;
import bf.fasoguardian.identite.domaine.LienTutelle;
import bf.fasoguardian.identite.domaine.NumeroTelephone;
import bf.fasoguardian.identite.domaine.NumeroTelephone.NumeroInvalideException;
import bf.fasoguardian.identite.domaine.Tuteur;
import bf.fasoguardian.identite.domaine.Utilisateur;
import bf.fasoguardian.identite.infrastructure.DepotDossiersKyc;
import bf.fasoguardian.identite.infrastructure.DepotInvitationsTuteur;
import bf.fasoguardian.identite.infrastructure.DepotLiensTutelle;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Second tuteur d'un enfant (US-PAR-003). Un tuteur invite un adulte par son numéro, après confirmation par
 * code SMS (action sensible, FG-DOC-06 §8.1) ; l'invité ouvre avec l'invitation son propre dossier KYC, et
 * c'est l'approbation de ce dossier qui lui donne un accès individuel à l'enfant. Un enfant a deux tuteurs
 * au plus (ADR 0032).
 */
@Service
public class InvitationsTuteur {

    public static final int TUTEURS_MAX = 2;
    private static final Set<Statut> OUVERTES = EnumSet.of(Statut.EN_ATTENTE, Statut.EN_VERIFICATION);
    private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM").withZone(ZoneId.of("Africa/Ouagadougou"));

    /**
     * Un tuteur de l'enfant, nommé comme son dossier KYC l'a vérifié (premier prénom et nom) ; « principal »
     * désigne celui dont la vérification a créé la fiche.
     */
    public record TuteurVue(String prenom, String nom, String telephone, boolean principal, boolean vous, boolean suspendu) {
    }

    public record InvitationVue(String telephone, Statut statut, Instant expireLe) {
    }

    public record TuteursEnfant(List<TuteurVue> tuteurs, InvitationVue invitation, boolean peutInviter) {
    }

    /** Invitation reçue, vue de l'invité : le prénom de qui l'invite, rien de l'enfant avant sa vérification. */
    public record InvitationRecue(UUID id, String invitant, Instant expireLe) {
    }

    private final DepotInvitationsTuteur invitations;
    private final DepotLiensTutelle liens;
    private final DepotUtilisateurs utilisateurs;
    private final DepotDossiersKyc dossiers;
    private final LiensTutelle annuaire;
    private final SecondFacteur secondFacteur;
    private final ServiceChiffrement chiffrement;
    private final ServiceSms sms;
    private final JournalAudit journal;
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final RegistrePurges registre;
    private final Clock horloge;

    InvitationsTuteur(DepotInvitationsTuteur invitations, DepotLiensTutelle liens, DepotUtilisateurs utilisateurs,
            DepotDossiersKyc dossiers, LiensTutelle annuaire, SecondFacteur secondFacteur, ServiceChiffrement chiffrement,
            ServiceSms sms, JournalAudit journal, JdbcTemplate jdbc, JsonMapper json, RegistrePurges registre, Clock horloge) {
        this.invitations = invitations;
        this.liens = liens;
        this.utilisateurs = utilisateurs;
        this.dossiers = dossiers;
        this.annuaire = annuaire;
        this.secondFacteur = secondFacteur;
        this.chiffrement = chiffrement;
        this.sms = sms;
        this.journal = journal;
        this.jdbc = jdbc;
        this.json = json;
        this.registre = registre;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- tuteur

    @Transactional(readOnly = true)
    public TuteursEnfant tuteurs(UUID tuteurId, UUID enfantId) {
        List<LienTutelle> liensEnfant = exigerTuteur(tuteurId, enfantId);
        UUID principal = liensEnfant.stream().min(Comparator.comparing(LienTutelle::creeLe)).map(LienTutelle::tuteurId).orElseThrow();
        List<TuteurVue> vues = liensEnfant.stream().sorted(Comparator.comparing(LienTutelle::creeLe))
                .map(lien -> new TuteurVue(annuaire.prenomDuTuteur(lien.tuteurId()).orElse(""), nomDe(lien.tuteurId()),
                        telephoneDe(lien.tuteurId()), lien.tuteurId().equals(principal), lien.tuteurId().equals(tuteurId),
                        !lien.actif()))
                .toList();
        Instant maintenant = horloge.instant();
        InvitationVue invitation = invitations.findFirstByEnfantIdAndStatutIn(enfantId, OUVERTES)
                .filter(i -> i.ouverte(maintenant)).map(this::vue).orElse(null);
        return new TuteursEnfant(vues, invitation, invitation == null && liensEnfant.size() < TUTEURS_MAX);
    }

    /** Invite un second tuteur ; le code SMS de l'action sensible n'est consommé qu'une fois la demande recevable. */
    @Transactional
    public InvitationVue inviter(UUID tuteurId, UUID enfantId, String saisieTelephone, String codeSecondFacteur) {
        List<LienTutelle> liensEnfant = exigerTuteur(tuteurId, enfantId);
        NumeroTelephone numero = numero(saisieTelephone);
        String empreinte = chiffrement.empreinte(CategorieDonnee.TELEPHONE, numero.e164());
        if (liensEnfant.size() >= TUTEURS_MAX) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Cet enfant a déjà deux tuteurs.");
        }
        boolean dejaTuteur = liensEnfant.stream().map(lien -> utilisateurs.findById(lien.tuteurId()))
                .flatMap(Optional::stream).anyMatch(u -> empreinte.equals(u.telephoneHash()));
        if (dejaTuteur) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce numéro est déjà celui d'un tuteur de l'enfant.");
        }
        Instant maintenant = horloge.instant();
        Optional<InvitationTuteur> existante = invitations.findFirstByEnfantIdAndStatutIn(enfantId, OUVERTES);
        if (existante.isPresent() && existante.get().ouverte(maintenant)) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Une invitation est déjà en cours pour cet enfant.");
        }
        secondFacteur.exiger(tuteurId, ActionSensible.AJOUTER_TUTEUR, codeSecondFacteur);
        existante.ifPresent(echue -> {
            echue.expirer(maintenant);
            invitations.saveAndFlush(echue);
        });
        InvitationTuteur invitation = invitations.save(new InvitationTuteur(enfantId, tuteurId,
                chiffrement.chiffrerTexte(CategorieDonnee.TELEPHONE, numero.e164()), empreinte, maintenant));
        journal.consigner(tuteurId, Tuteur.ROLE, "INVITATION_TUTEUR", "ENFANT", enfantId.toString(), Resultat.SUCCES);
        String invitant = annuaire.prenomDuTuteur(tuteurId).orElse("Un parent");
        sms.envoyer(numero.e164(), "FasoGuardian : " + invitant + " vous invite comme second tuteur d'un enfant. "
                + "Ouvrez l'application FasoGuardian avec ce numéro avant le " + JOUR.format(invitation.expireLe())
                + " : vous passerez votre propre vérification d'identité.");
        return vue(invitation);
    }

    @Transactional
    public void annuler(UUID tuteurId, UUID enfantId) {
        exigerTuteur(tuteurId, enfantId);
        InvitationTuteur invitation = invitations.findFirstByEnfantIdAndStatutIn(enfantId, OUVERTES)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucune invitation en cours."));
        invitation.annuler(horloge.instant());
        journal.consigner(tuteurId, Tuteur.ROLE, "ANNULATION_INVITATION_TUTEUR", "ENFANT", enfantId.toString(), Resultat.SUCCES);
    }

    // ----------------------------------------------------------------- invité

    @Transactional(readOnly = true)
    public List<InvitationRecue> recues(UUID utilisateurId) {
        Instant maintenant = horloge.instant();
        String empreinte = utilisateurs.findById(utilisateurId).map(Utilisateur::telephoneHash).orElse(null);
        if (empreinte == null) {
            return List.of();
        }
        return invitations.findByTelephoneHashAndStatut(empreinte, Statut.EN_ATTENTE).stream()
                .filter(i -> i.ouverte(maintenant))
                .map(i -> new InvitationRecue(i.id(), annuaire.prenomDuTuteur(i.invitantId()).orElse(""), i.expireLe()))
                .toList();
    }

    /**
     * Réserve l'invitation au dossier que l'invité ouvre : elle doit lui être adressée et encore ouverte.
     * Appelée dans la transaction de l'ouverture du dossier.
     */
    InvitationTuteur reserverPourDossier(UUID utilisateurId, UUID invitationId) {
        Instant maintenant = horloge.instant();
        String empreinte = utilisateurs.findById(utilisateurId).map(Utilisateur::telephoneHash).orElse(null);
        InvitationTuteur invitation = invitations.findById(invitationId)
                .filter(i -> i.statut() == Statut.EN_ATTENTE && !i.expiree(maintenant))
                .filter(i -> empreinte != null && empreinte.equals(i.telephoneHash()))
                .orElseThrow(() -> {
                    journal.consigner(utilisateurId, Tuteur.ROLE, "ACCES_REFUSE", "INVITATION_TUTEUR", invitationId.toString(), Resultat.REFUS);
                    return new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Invitation introuvable ou expirée.");
                });
        if (liens.findByTuteurIdAndEnfantId(utilisateurId, invitation.enfantId()).isPresent()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Vous êtes déjà tuteur de cet enfant.");
        }
        invitation.ouvrirDossier();
        return invitation;
    }

    /** Identité déclarée de l'enfant, chiffrée, telle que vérifiée lors du dossier qui a créé sa fiche. */
    byte[] enfantDeclare(UUID enfantId) {
        return dossiers.findFirstByEnfantIdAndStatutOrderByDecideLeAsc(enfantId, DossierKyc.Statut.APPROUVE)
                .map(DossierKyc::enfantChiffre)
                .orElseThrow(() -> new IllegalStateException("Aucun dossier approuvé pour l'enfant " + enfantId));
    }

    // ------------------------------------------------------- décision de l'agent

    /** Avant d'approuver le dossier d'un invité : l'invitation est toujours voulue et la place libre. */
    void exigerApprouvable(UUID invitationId, UUID enfantId) {
        boolean ouverte = invitations.findById(invitationId).filter(i -> i.statut() == Statut.EN_VERIFICATION).isPresent();
        if (!ouverte) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "L'invitation a été annulée par le tuteur : ce dossier ne peut être que rejeté.");
        }
        if (liens.findByEnfantId(enfantId).size() >= TUTEURS_MAX) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "L'enfant a déjà deux tuteurs : ce dossier ne peut être que rejeté.");
        }
    }

    /** L'approbation clôt l'invitation et prévient celui qui l'a envoyée ; un rejet la clôt aussi. */
    void surDecision(UUID invitationId, boolean approuve) {
        Instant maintenant = horloge.instant();
        invitations.findById(invitationId).filter(i -> i.statut().ouverte()).ifPresent(invitation -> {
            if (approuve) {
                invitation.accepter(maintenant);
                utilisateurs.findById(invitation.invitantId()).filter(Utilisateur::peutSAuthentifier).ifPresent(invitant ->
                        sms.envoyer(chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, invitant.telephoneChiffre()),
                                "FasoGuardian : le second tuteur que vous avez invité est vérifié. Il suit désormais l'enfant avec vous."));
            } else {
                invitation.annuler(maintenant);
            }
        });
    }

    // ------------------------------------------------------------------ purge

    /** Les invitations échues passent à l'état expiré, sans numéro ; une invitation close est supprimée après 30 jours. */
    @Scheduled(cron = "${fasoguardian.identite.purge-invitations:0 15 3 * * *}")
    @SchedulerLock(name = "identite-purge-invitations")
    @Transactional
    public void purger() {
        long supprimes = jdbc.update("UPDATE identite.invitation_tuteur SET statut = 'EXPIREE', close_le = expire_le,"
                + " telephone_chiffre = NULL, telephone_hash = NULL WHERE statut = 'EN_ATTENTE' AND expire_le <= now()");
        supprimes += jdbc.update("DELETE FROM identite.invitation_tuteur WHERE close_le < now() - INTERVAL '30 days'");
        registre.consigner("INVITATIONS_TUTEUR", supprimes);
    }

    // ------------------------------------------------------------------ aides

    /** Liens de l'enfant, si le demandeur en est tuteur actif ; sinon l'existence de l'enfant n'est pas révélée. */
    private List<LienTutelle> exigerTuteur(UUID tuteurId, UUID enfantId) {
        List<LienTutelle> liensEnfant = liens.findByEnfantId(enfantId);
        if (liensEnfant.stream().noneMatch(lien -> lien.tuteurId().equals(tuteurId) && lien.actif())) {
            journal.consigner(tuteurId, Tuteur.ROLE, "ACCES_REFUSE", "ENFANT", enfantId.toString(), Resultat.REFUS);
            throw new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Enfant introuvable.");
        }
        return liensEnfant;
    }

    private String nomDe(UUID tuteurId) {
        return dossiers.findFirstByDemandeurIdAndStatutOrderByDecideLeDesc(tuteurId, DossierKyc.Statut.APPROUVE)
                .map(dossier -> json.readValue(chiffrement.dechiffrer(CategorieDonnee.PIECE_KYC, dossier.identiteChiffree()),
                        InstructionKyc.IdentiteDeclaree.class).nom())
                .orElse("");
    }

    private String telephoneDe(UUID tuteurId) {
        return utilisateurs.findById(tuteurId).map(Utilisateur::telephoneChiffre)
                .map(chiffre -> chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, chiffre)).orElse(null);
    }

    private InvitationVue vue(InvitationTuteur invitation) {
        return new InvitationVue(chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, invitation.telephoneChiffre()),
                invitation.statut(), invitation.expireLe());
    }

    private static NumeroTelephone numero(String saisie) {
        try {
            return NumeroTelephone.depuisSaisie(saisie);
        } catch (NumeroInvalideException erreur) {
            throw new ErreurMetier(CodeErreur.TELEPHONE_INVALIDE, "Saisissez un numéro mobile burkinabè.");
        }
    }
}
