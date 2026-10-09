package bf.fasoguardian.identite.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.identite.application.InstructionKyc.EnfantDeclare;
import bf.fasoguardian.identite.application.InstructionKyc.IdentiteDeclaree;
import bf.fasoguardian.identite.domaine.DossierKyc;
import bf.fasoguardian.identite.domaine.LienTutelle;
import bf.fasoguardian.identite.domaine.Litige;
import bf.fasoguardian.identite.domaine.Litige.Decision;
import bf.fasoguardian.identite.domaine.Litige.Fondement;
import bf.fasoguardian.identite.domaine.Litige.Statut;
import bf.fasoguardian.identite.infrastructure.DepotDossiersKyc;
import bf.fasoguardian.identite.infrastructure.DepotLiensTutelle;
import bf.fasoguardian.identite.infrastructure.DepotLitiges;
import bf.fasoguardian.notifications.Notifications;
import bf.fasoguardian.notifications.Notifications.Message;
import bf.fasoguardian.notifications.Notifications.Urgence;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Instruction des litiges de filiation par l'agent KYC (US-KYC-001). L'ouverture gèle, pour le tuteur
 * contesté, tout ce qui se modifie autour de l'enfant, et peut suspendre sa vue de la position. La décision,
 * fondée sur une décision de justice ou un accord écrit, est appliquée, journalisée et notifiée aux parties.
 * Le SOS, les alertes et la page publique du bracelet ne sont jamais suspendus.
 */
@Service
public class Litiges {

    private static final String ROLE = "KYC";

    /** Ce qui s'applique en ce moment à un tuteur pour un enfant. */
    public record Mesures(boolean compteGele, boolean geolocalisationSuspendue) {

        public static final Mesures AUCUNE = new Mesures(false, false);
    }

    /** @param tuteur et {@code enfant} : identités vérifiées du dossier KYC, visibles du seul agent KYC */
    public record LitigeVue(UUID id, String reference, String dossierKyc, String tuteur, String enfant, String motif,
            Statut statut, boolean geolocalisationSuspendue, Instant ouvertLe, Instant echeanceLe, Decision decision,
            Fondement fondement, String referenceDuFondement, Instant decideLe) {
    }

    private final DepotLitiges litiges;
    private final DepotDossiersKyc dossiers;
    private final DepotLiensTutelle liens;
    private final Sessions sessions;
    private final Notifications notifications;
    private final ServiceChiffrement chiffrement;
    private final JsonMapper json;
    private final JournalAudit journal;
    private final Clock horloge;

    Litiges(DepotLitiges litiges, DepotDossiersKyc dossiers, DepotLiensTutelle liens, Sessions sessions, Notifications notifications,
            ServiceChiffrement chiffrement, JsonMapper json, JournalAudit journal, Clock horloge) {
        this.litiges = litiges;
        this.dossiers = dossiers;
        this.liens = liens;
        this.sessions = sessions;
        this.notifications = notifications;
        this.chiffrement = chiffrement;
        this.json = json;
        this.journal = journal;
        this.horloge = horloge;
    }

    /** Mesures conservatoires en vigueur ; appelé à chaque requête d'un tuteur sur un enfant. */
    @Transactional(readOnly = true)
    public Mesures mesures(UUID tuteurId, UUID enfantId) {
        return litiges.findByTuteurIdAndEnfantIdAndStatut(tuteurId, enfantId, Statut.OUVERT)
                .map(litige -> new Mesures(true, litige.geolocalisationSuspendue())).orElse(Mesures.AUCUNE);
    }

    /** Vrai si le tuteur est partie à un litige ouvert, pour quelque enfant que ce soit. */
    @Transactional(readOnly = true)
    public boolean enLitige(UUID tuteurId) {
        return !litiges.findByTuteurIdAndStatut(tuteurId, Statut.OUVERT).isEmpty();
    }

    @Transactional(readOnly = true)
    public List<LitigeVue> litiges() {
        return litiges.findTop200ByOrderByOuvertLeDesc().stream().map(this::vue).toList();
    }

    @Transactional
    public LitigeVue litige(UUID agentId, UUID litigeId) {
        Litige litige = trouver(litigeId);
        journal.consigner(agentId, ROLE, "LITIGE_CONSULTE", "LITIGE", litige.reference(), Resultat.SUCCES);
        return vue(litige);
    }

    /**
     * Ouvre un litige sur le lien établi par un dossier KYC approuvé.
     *
     * @param referenceDossier référence du dossier (KYC-…) qui a établi le lien contesté
     */
    @Transactional
    public LitigeVue ouvrir(UUID agentId, String referenceDossier, String motif, boolean suspendreLaGeolocalisation) {
        DossierKyc dossier = dossiers.findByReference(referenceDossier == null ? "" : referenceDossier.strip().toUpperCase(Locale.ROOT))
                .filter(d -> d.statut() == DossierKyc.Statut.APPROUVE && d.enfantId() != null)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE,
                        "Aucun dossier KYC approuvé ne porte cette référence."));
        if (motif == null || motif.isBlank()) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Décrivez le signalement reçu.");
        }
        if (litiges.findByTuteurIdAndEnfantIdAndStatut(dossier.demandeurId(), dossier.enfantId(), Statut.OUVERT).isPresent()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Un litige est déjà ouvert sur ce lien.");
        }
        Litige litige = litiges.save(new Litige(litiges.prochainNumero(), dossier.demandeurId(), dossier.enfantId(), motif.strip(),
                suspendreLaGeolocalisation, agentId, horloge.instant()));
        journal.consigner(agentId, ROLE, "LITIGE_OUVERT", "LITIGE", litige.reference(), Resultat.SUCCES);
        prevenir(litige, "LITIGE_OUVERT", "Compte en cours de vérification",
                "FasoGuardian : un signalement concernant votre lien avec votre enfant est en cours d'instruction (" + litige.reference()
                        + "). Les réglages sont gelés" + (suspendreLaGeolocalisation ? " et la position suspendue" : "")
                        + " jusqu'à la décision. Le SOS et la page QR restent actifs.");
        return vue(litige);
    }

    /** Pose ou lève la suspension conservatoire de la géolocalisation pendant l'instruction. */
    @Transactional
    public LitigeVue suspendreLaGeolocalisation(UUID agentId, UUID litigeId, boolean suspendue) {
        Litige litige = trouver(litigeId);
        if (litige.suspendreLaGeolocalisation(suspendue)) {
            journal.consigner(agentId, ROLE, suspendue ? "GEOLOCALISATION_SUSPENDUE" : "GEOLOCALISATION_RETABLIE", "LITIGE",
                    litige.reference(), Resultat.SUCCES);
        }
        return vue(litige);
    }

    /** Enregistre la décision, l'applique et la notifie aux parties. */
    @Transactional
    public LitigeVue decider(UUID agentId, UUID litigeId, Decision decision, Fondement fondement, String referenceDuFondement) {
        Litige litige = trouver(litigeId);
        if (decision == null || fondement == null) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE,
                    "Une décision se fonde sur une décision de justice ou un accord écrit : indiquez lequel.");
        }
        Instant maintenant = horloge.instant();
        if (!litige.decider(decision, fondement, referenceDuFondement, agentId, maintenant)) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce litige est déjà clos.");
        }
        // Les parties sont prévenues avant l'application : le tuteur dont le lien est retiré doit l'être aussi.
        prevenir(litige, "LITIGE_CLOS", "Décision rendue", "FasoGuardian : le signalement " + litige.reference()
                + " est clos. " + (decision == Decision.LIEN_RETIRE ? "L'accès contesté à l'enfant est retiré."
                        : "L'accès à l'enfant est maintenu et les réglages sont de nouveau modifiables."));
        if (decision == Decision.LIEN_RETIRE) {
            liens.findByTuteurIdAndEnfantId(litige.tuteurId(), litige.enfantId()).ifPresent(LienTutelle::suspendre);
            sessions.fermerToutes(litige.tuteurId(), maintenant);
        }
        journal.consigner(agentId, ROLE, "LITIGE_DECIDE_" + decision.name(), "LITIGE", litige.reference(), Resultat.SUCCES);
        return vue(litige);
    }

    // -------------------------------------------------------------------- aides

    private Litige trouver(UUID litigeId) {
        return litiges.findById(litigeId)
                .orElseThrow(() -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Litige introuvable."));
    }

    /** Le tuteur contesté et les autres tuteurs actifs de l'enfant. */
    private void prevenir(Litige litige, String modele, String titre, String texte) {
        Message message = Message.simple(modele, titre, texte);
        liens.findByEnfantId(litige.enfantId()).stream().filter(lien -> lien.actif() || lien.tuteurId().equals(litige.tuteurId()))
                .map(LienTutelle::tuteurId).distinct().forEach(tuteur -> notifications.notifier(tuteur, Urgence.IMPORTANTE, message));
    }

    private LitigeVue vue(Litige litige) {
        DossierKyc dossier = dossiers.findFirstByDemandeurIdAndStatutOrderByDecideLeDesc(litige.tuteurId(), DossierKyc.Statut.APPROUVE)
                .orElse(null);
        String tuteur = null;
        String enfant = null;
        if (dossier != null) {
            IdentiteDeclaree identite = json.readValue(chiffrement.dechiffrer(CategorieDonnee.PIECE_KYC, dossier.identiteChiffree()),
                    IdentiteDeclaree.class);
            EnfantDeclare declare = json.readValue(chiffrement.dechiffrer(CategorieDonnee.PIECE_KYC, dossier.enfantChiffre()),
                    EnfantDeclare.class);
            tuteur = identite.nom().toUpperCase(Locale.FRENCH) + " " + identite.prenoms();
            enfant = declare.prenom();
        }
        return new LitigeVue(litige.id(), litige.reference(), dossier == null ? null : dossier.reference(), tuteur, enfant,
                litige.motif(), litige.statut(), litige.geolocalisationSuspendue(), litige.ouvertLe(), litige.echeanceLe(),
                litige.decision(), litige.fondement(), litige.referenceDuFondement(), litige.decideLe());
    }
}
