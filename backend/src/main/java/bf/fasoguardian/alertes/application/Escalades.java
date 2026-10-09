package bf.fasoguardian.alertes.application;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import bf.fasoguardian.alertes.application.RedacteurDossier.Contenu;
import bf.fasoguardian.alertes.domaine.ActionAlerte;
import bf.fasoguardian.alertes.domaine.Alerte;
import bf.fasoguardian.alertes.domaine.Alerte.Statut;
import bf.fasoguardian.alertes.domaine.Alerte.TransitionIllegaleException;
import bf.fasoguardian.alertes.domaine.SignalementFds;
import bf.fasoguardian.alertes.domaine.SignalementFds.Canal;
import bf.fasoguardian.alertes.infrastructure.DepotActions;
import bf.fasoguardian.alertes.infrastructure.DepotAlertes;
import bf.fasoguardian.alertes.infrastructure.DepotSignalements;
import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.RegistrePurges;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.dispositifs.Bracelets;
import bf.fasoguardian.dispositifs.Bracelets.BraceletConnu;
import bf.fasoguardian.famille.AccesEnfant;
import bf.fasoguardian.famille.DossiersEnfants;
import bf.fasoguardian.famille.DossiersEnfants.Identification;
import bf.fasoguardian.identite.SecondFacteur;
import bf.fasoguardian.identite.SecondFacteur.ActionSensible;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import bf.fasoguardian.telemetrie.TrajetsRecents;
import bf.fasoguardian.telemetrie.TrajetsRecents.Point;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Escalade d'une alerte vers les forces de sécurité (US-PAR-010, REQ-SYS-022). Le parent, seul décisionnaire,
 * la confirme par second facteur. Avec une convention active, le dossier est transmis ; sans convention, il
 * est généré pour que le parent le remette lui-même. Dans les deux cas, la transmission est journalisée et
 * le dossier, chiffré, n'est gardé que 30 jours.
 */
@Service
public class Escalades {

    private static final String ROLE = "PARENT";
    /** Durée du trajet récent joint au dossier. */
    static final Duration TRAJET_JOINT = Duration.ofHours(2);

    /** Ce que le dossier contiendra, montré au parent avant qu'il confirme. */
    public record Apercu(Identification enfant, int positionsDuTrajet, Point dernierePosition, boolean conventionActive) {
    }

    public record SignalementVue(String reference, Canal canal, Instant creeLe, Instant disponibleJusquAu,
            boolean dossierDisponible, String empreinteDossier) {
    }

    public record Dossier(String reference, byte[] pdf) {
    }

    private final DepotAlertes alertes;
    private final DepotActions actions;
    private final DepotSignalements signalements;
    private final AccesEnfant acces;
    private final SecondFacteur secondFacteur;
    private final DossiersEnfants enfants;
    private final TrajetsRecents trajets;
    private final Bracelets bracelets;
    private final RedacteurDossier redacteur;
    private final PasserelleFds passerelle;
    private final ServiceChiffrement chiffrement;
    private final RegistrePurges registre;
    private final JournalAudit journal;
    private final Clock horloge;
    private final DateTimeFormatter dateHeure;

    Escalades(DepotAlertes alertes, DepotActions actions, DepotSignalements signalements, AccesEnfant acces,
            SecondFacteur secondFacteur, DossiersEnfants enfants, TrajetsRecents trajets, Bracelets bracelets,
            RedacteurDossier redacteur,
            PasserelleFds passerelle, ServiceChiffrement chiffrement, JournalAudit journal, RegistrePurges registre, Clock horloge,
            @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.alertes = alertes;
        this.registre = registre;
        this.actions = actions;
        this.signalements = signalements;
        this.acces = acces;
        this.secondFacteur = secondFacteur;
        this.enfants = enfants;
        this.trajets = trajets;
        this.bracelets = bracelets;
        this.redacteur = redacteur;
        this.passerelle = passerelle;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.horloge = horloge;
        this.dateHeure = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", Locale.FRENCH).withZone(fuseau);
    }

    /** Aperçu du dossier ; il lit des données sensibles, la consultation est donc journalisée. */
    @Transactional
    public Apercu apercu(UUID tuteurId, UUID alerteId) {
        Alerte alerte = pourTuteur(tuteurId, alerteId);
        journal.consigner(tuteurId, ROLE, "APERCU_SIGNALEMENT_CONSULTE", "ALERTE", alerteId.toString(), Resultat.SUCCES);
        List<Point> trajet = trajets.depuis(alerte.enfantId(), horloge.instant().minus(TRAJET_JOINT));
        return new Apercu(enfants.pourSignalement(alerte.enfantId()), trajet.size(),
                trajet.isEmpty() ? null : trajet.get(trajet.size() - 1), passerelle.conventionActive());
    }

    @Transactional
    public SignalementVue escalader(UUID tuteurId, UUID alerteId, String codeSecondFacteur) {
        Alerte alerte = pourTuteur(tuteurId, alerteId);
        if (alerte.statut() != Statut.ACQUITTEE) {
            throw conflit();
        }
        secondFacteur.exiger(tuteurId, ActionSensible.ESCALADER_FORCES_SECURITE, codeSecondFacteur);
        Instant maintenant = horloge.instant();
        ActionAlerte escalade;
        try {
            escalade = alerte.escalader(tuteurId, maintenant);
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        actions.saveAndFlush(escalade);

        String reference = String.format(Locale.ROOT, "FG-SIG-%06d", signalements.prochainNumero());
        List<String> lignesDuJournal = actions.findByAlerteIdInOrderByEffectueeLe(List.of(alerteId)).stream()
                .map(action -> dateHeure.format(action.effectueeLe()) + " — " + libelle(action)).toList();
        byte[] pdf = redacteur.rediger(new Contenu(reference, maintenant, enfants.pourSignalement(alerte.enfantId()),
                bracelets.deLEnfant(alerte.enfantId()).map(BraceletConnu::numeroSerie).orElse(null), libelle(alerte),
                alerte.ouverteLe(), lignesDuJournal,
                trajets.depuis(alerte.enfantId(), maintenant.minus(TRAJET_JOINT))));

        Canal canal = passerelle.conventionActive() ? Canal.PASSERELLE : Canal.REMISE_PAR_LE_PARENT;
        if (canal == Canal.PASSERELLE) {
            passerelle.transmettre(reference, pdf);
        }
        SignalementFds signalement = signalements.save(new SignalementFds(alerteId, reference, canal, sha256(pdf),
                chiffrement.chiffrer(CategorieDonnee.SIGNALEMENT, pdf), maintenant));
        journal.consigner(tuteurId, ROLE, "ALERTE_ESCALADEE", "ALERTE", alerteId.toString(), Resultat.SUCCES);
        journal.consigner(tuteurId, ROLE, canal == Canal.PASSERELLE ? "DOSSIER_SIGNALEMENT_TRANSMIS" : "DOSSIER_SIGNALEMENT_GENERE",
                "ALERTE", alerteId.toString(), Resultat.SUCCES);
        return vue(signalement);
    }

    @Transactional(readOnly = true)
    public SignalementVue signalement(UUID tuteurId, UUID alerteId) {
        pourTuteur(tuteurId, alerteId);
        return signalements.findById(alerteId).map(Escalades::vue).orElseThrow(Escalades::introuvable);
    }

    /** Dossier PDF, pour remise aux autorités ; chaque téléchargement est journalisé. */
    @Transactional
    public Dossier dossier(UUID tuteurId, UUID alerteId) {
        pourTuteur(tuteurId, alerteId);
        SignalementFds signalement = signalements.findById(alerteId).filter(SignalementFds::dossierDisponible)
                .orElseThrow(Escalades::introuvable);
        journal.consigner(tuteurId, ROLE, "DOSSIER_SIGNALEMENT_TELECHARGE", "ALERTE", alerteId.toString(), Resultat.SUCCES);
        return new Dossier(signalement.reference(), chiffrement.dechiffrer(CategorieDonnee.SIGNALEMENT, signalement.dossierChiffre()));
    }

    /** Efface les dossiers de plus de 30 jours ; la référence et l'empreinte restent. */
    @Scheduled(cron = "${fasoguardian.alertes.purge-dossiers:0 40 2 * * *}")
    @SchedulerLock(name = "alertes-purge-dossiers")
    @Transactional
    public void effacerLesDossiersEchus() {
        Instant maintenant = horloge.instant();
        List<SignalementFds> echus = signalements.findByDossierChiffreIsNotNullAndCreeLeBefore(
                maintenant.minus(SignalementFds.CONSERVATION_DU_DOSSIER));
        echus.forEach(signalement -> {
            signalement.effacerDossier(maintenant);
            journal.consigner(null, "SYSTEME", "DOSSIER_SIGNALEMENT_EFFACE", "ALERTE", signalement.alerteId().toString(),
                    Resultat.SUCCES);
        });
        registre.consigner("DOSSIERS_DE_SIGNALEMENT", echus.size());
    }

    // -------------------------------------------------------------------- aides

    private Alerte pourTuteur(UUID tuteurId, UUID alerteId) {
        Alerte alerte = alertes.findById(alerteId).orElseThrow(Escalades::introuvable);
        acces.exigerTuteur(tuteurId, alerte.enfantId());
        return alerte;
    }

    private static SignalementVue vue(SignalementFds s) {
        return new SignalementVue(s.reference(), s.canal(), s.creeLe(), s.disponibleJusquAu(), s.dossierDisponible(),
                s.empreinteDossier());
    }

    private static String libelle(Alerte alerte) {
        return switch (alerte.type()) {
            case SOS -> "SOS déclenché depuis le bracelet";
            case RETRAIT -> "Retrait non autorisé du bracelet";
            case SIGNALEMENT -> "Signalement par un tuteur";
            case SORTIE_ZONE -> "Sortie de la zone « " + alerte.libelle() + " »";
            case BATTERIE_CRITIQUE -> "Batterie critique du bracelet";
            case CHUTE -> "Chute détectée par le bracelet";
        };
    }

    private static String libelle(ActionAlerte action) {
        String quoi = switch (action.type()) {
            case OUVERTURE -> "alerte déclenchée";
            case ACQUITTEMENT -> "prise en charge par un tuteur";
            case ESCALADE -> "escalade confirmée par un tuteur";
            case LEVEE -> "alerte levée";
            case FAUSSE_ALERTE -> "classée fausse alerte";
            case RESOLUTION -> "levée automatiquement";
            case CONTACT_SOLLICITE -> "contacts d'urgence sollicités faute de réponse";
            case INSTITUTION_SOLLICITEE -> "point de contact institutionnel sollicité";
        };
        return action.motif() == null ? quoi : quoi + " (" + action.motif() + ")";
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    private static ErreurMetier conflit() {
        return new ErreurMetier(CodeErreur.CONFLIT, "Seule une alerte que vous avez prise en charge peut être signalée aux autorités.");
    }

    private static ErreurMetier introuvable() {
        return new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Signalement introuvable.");
    }
}
