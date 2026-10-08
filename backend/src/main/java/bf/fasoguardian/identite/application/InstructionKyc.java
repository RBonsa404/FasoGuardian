package bf.fasoguardian.identite.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import bf.fasoguardian.audit.JournalAudit;
import bf.fasoguardian.audit.JournalAudit.Resultat;
import bf.fasoguardian.identite.DossierKycApprouve;
import bf.fasoguardian.identite.domaine.DossierKyc;
import bf.fasoguardian.identite.domaine.DossierKyc.Canal;
import bf.fasoguardian.identite.domaine.DossierKyc.DossierIncompletException;
import bf.fasoguardian.identite.domaine.DossierKyc.NatureLien;
import bf.fasoguardian.identite.domaine.DossierKyc.Statut;
import bf.fasoguardian.identite.domaine.DossierKyc.TransitionIllegaleException;
import bf.fasoguardian.identite.domaine.DossierKyc.TypePiece;
import bf.fasoguardian.identite.domaine.LienTutelle;
import bf.fasoguardian.identite.domaine.NumeroTelephone;
import bf.fasoguardian.identite.domaine.PieceJustificative;
import bf.fasoguardian.identite.domaine.RoleInterne;
import bf.fasoguardian.identite.domaine.Tuteur;
import bf.fasoguardian.identite.infrastructure.DepotUtilisateurs;
import bf.fasoguardian.identite.infrastructure.DepotDossiersKyc;
import bf.fasoguardian.identite.infrastructure.DepotLiensTutelle;
import bf.fasoguardian.identite.infrastructure.DepotPiecesKyc;
import bf.fasoguardian.notifications.ServiceSms;
import bf.fasoguardian.plateforme.chiffrement.CategorieDonnee;
import bf.fasoguardian.plateforme.chiffrement.ServiceChiffrement;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Vérification KYC du lien de filiation ou de tutelle (US-PAR-001, REQ-SYS-013). Le parent constitue et
 * dépose son dossier ; un agent KYC l'instruit. Identités et pièces sont chiffrées ; chaque consultation
 * par un agent est journalisée, et seul le rôle KYC y accède (contrôlé à l'entrée des API).
 */
@Service
public class InstructionKyc {

    public static final int TAILLE_MAX_PIECE = 5 * 1024 * 1024;
    private static final Set<String> TYPES_MIME = Set.of("image/jpeg", "image/png", "application/pdf");
    private static final Set<Statut> OUVERTS =
            EnumSet.of(Statut.BROUILLON, Statut.DEPOSE, Statut.EN_INSTRUCTION, Statut.COMPLEMENT_DEMANDE);

    public record IdentiteDeclaree(String nom, String prenoms, String typePiece, String numeroPiece) {
    }

    public record EnfantDeclare(String prenom, String nom, LocalDate dateNaissance) {
    }

    public record PieceVue(UUID id, TypePiece type, String typeMime, int tailleOctets) {
    }

    /** Vue du parent : aucun contenu de pièce, aucune identité de l'agent. */
    public record DossierParent(UUID id, String reference, Statut statut, Canal canal, NatureLien natureLien,
            String motif, Instant deposeLe, Instant decideLe, List<PieceVue> pieces) {
    }

    /** Ligne de la file d'instruction : aucune donnée d'identité. */
    public record DossierFile(UUID id, String reference, Statut statut, Canal canal, Instant deposeLe, boolean prisEnCharge) {
    }

    public record DossierInstruction(UUID id, String reference, Statut statut, Canal canal, NatureLien natureLien,
            IdentiteDeclaree demandeur, EnfantDeclare enfant, String motif, Instant deposeLe, List<PieceVue> pieces) {
    }

    public record ContenuPiece(byte[] octets, String typeMime) {
    }

    public enum Decision {
        APPROUVER,
        REJETER,
        DEMANDER_COMPLEMENT
    }

    private final DepotDossiersKyc dossiers;
    private final DepotPiecesKyc pieces;
    private final DepotLiensTutelle liens;
    private final DepotUtilisateurs utilisateurs;
    private final ServiceChiffrement chiffrement;
    private final JournalAudit journal;
    private final ServiceSms sms;
    private final ApplicationEventPublisher evenements;
    private final JsonMapper json;
    private final Clock horloge;

    InstructionKyc(DepotDossiersKyc dossiers, DepotPiecesKyc pieces, DepotLiensTutelle liens,
            DepotUtilisateurs utilisateurs, ServiceChiffrement chiffrement, JournalAudit journal, ServiceSms sms,
            ApplicationEventPublisher evenements, JsonMapper json, Clock horloge) {
        this.dossiers = dossiers;
        this.pieces = pieces;
        this.liens = liens;
        this.utilisateurs = utilisateurs;
        this.chiffrement = chiffrement;
        this.journal = journal;
        this.sms = sms;
        this.evenements = evenements;
        this.json = json;
        this.horloge = horloge;
    }

    // ---------------------------------------------------------------- parent

    @Transactional
    public DossierParent ouvrir(UUID tuteurId, Canal canal, NatureLien nature, IdentiteDeclaree demandeur,
            EnfantDeclare enfant) {
        if (dossiers.existsByDemandeurIdAndStatutIn(tuteurId, OUVERTS)) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Un dossier de vérification est déjà en cours.");
        }
        if (enfant.dateNaissance().isAfter(LocalDate.now(horloge))
                || enfant.dateNaissance().isBefore(LocalDate.now(horloge).minusYears(18))) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Le service s'adresse aux enfants de moins de 18 ans.");
        }
        DossierKyc dossier = dossiers.save(new DossierKyc("KYC-" + dossiers.prochaineReference(), tuteurId, canal, nature,
                chiffrerJson(demandeur), chiffrerJson(enfant), horloge.instant()));
        return vueParent(dossier);
    }

    // Un refus n'annule pas la transaction : sa trace au journal d'audit doit être conservée.
    @Transactional(noRollbackFor = ErreurMetier.class)
    public PieceVue ajouterPiece(UUID tuteurId, UUID dossierId, TypePiece type, byte[] contenu, String typeMime) {
        DossierKyc dossier = duParent(tuteurId, dossierId);
        if (!dossier.piecesModifiables()) {
            throw new ErreurMetier(CodeErreur.CONFLIT, "Ce dossier n'accepte plus de pièce.");
        }
        if (contenu == null || contenu.length == 0 || contenu.length > TAILLE_MAX_PIECE || !TYPES_MIME.contains(typeMime)
                || !signatureConforme(contenu, typeMime)) {
            throw new ErreurMetier(CodeErreur.PIECE_REFUSEE,
                    "Envoyez une photo JPEG ou PNG, ou un PDF, de 5 Mo au plus.");
        }
        pieces.findByDossierIdAndType(dossierId, type).ifPresent(ancienne -> {
            pieces.delete(ancienne);
            pieces.flush();
        });
        PieceJustificative piece = pieces.save(new PieceJustificative(dossierId, type,
                chiffrement.chiffrer(CategorieDonnee.PIECE_KYC, contenu), typeMime, sha256(contenu), contenu.length,
                horloge.instant()));
        return vue(piece);
    }

    // Un refus n'annule pas la transaction : sa trace au journal d'audit doit être conservée.
    @Transactional(noRollbackFor = ErreurMetier.class)
    public DossierParent deposer(UUID tuteurId, UUID dossierId) {
        DossierKyc dossier = duParent(tuteurId, dossierId);
        Set<TypePiece> presentes = pieces.findByDossierId(dossierId).stream().map(PieceJustificative::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(TypePiece.class)));
        try {
            dossier.deposer(presentes, horloge.instant());
        } catch (DossierIncompletException erreur) {
            throw new ErreurMetier(CodeErreur.DOSSIER_INCOMPLET,
                    "Ajoutez le recto de votre pièce d'identité et le justificatif du lien avec l'enfant.");
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        return vueParent(dossier);
    }

    @Transactional(readOnly = true)
    public Optional<DossierParent> courant(UUID tuteurId) {
        return dossiers.findFirstByDemandeurIdOrderByCreeLeDesc(tuteurId).map(this::vueParent);
    }

    // ----------------------------------------------------------------- agent

    @Transactional(readOnly = true)
    public Page<DossierFile> file(Set<Statut> statuts, int page, int taille) {
        Set<Statut> filtre = statuts == null || statuts.isEmpty() ? EnumSet.of(Statut.DEPOSE, Statut.EN_INSTRUCTION) : statuts;
        return dossiers.findByStatutInOrderByDeposeLeAsc(filtre, PageRequest.of(Math.max(page, 0), Math.clamp(taille, 1, 100)))
                .map(d -> new DossierFile(d.id(), d.reference(), d.statut(), d.canal(), d.deposeLe(), d.agentId() != null));
    }

    @Transactional
    public DossierInstruction consulter(UUID agentId, UUID dossierId) {
        DossierKyc dossier = pourAgent(dossierId);
        journal.consigner(agentId, RoleInterne.KYC.name(), "CONSULTATION_DOSSIER_KYC", "DOSSIER_KYC",
                dossierId.toString(), Resultat.SUCCES);
        return vueInstruction(dossier);
    }

    @Transactional
    public DossierInstruction prendreEnCharge(UUID agentId, UUID dossierId) {
        DossierKyc dossier = pourAgent(dossierId);
        try {
            dossier.prendreEnCharge(agentId);
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        }
        journal.consigner(agentId, RoleInterne.KYC.name(), "PRISE_EN_CHARGE_KYC", "DOSSIER_KYC", dossierId.toString(),
                Resultat.SUCCES);
        return vueInstruction(dossier);
    }

    @Transactional
    public ContenuPiece lirePiece(UUID agentId, UUID dossierId, UUID pieceId) {
        pourAgent(dossierId);
        PieceJustificative piece = pieces.findById(pieceId).filter(p -> p.dossierId().equals(dossierId))
                .orElseThrow(InstructionKyc::introuvable);
        byte[] contenu = chiffrement.dechiffrer(CategorieDonnee.PIECE_KYC, piece.contenuChiffre());
        if (!sha256(contenu).equals(piece.sha256())) {
            throw new IllegalStateException("Pièce KYC altérée : empreinte non conforme");
        }
        journal.consigner(agentId, RoleInterne.KYC.name(), "CONSULTATION_PIECE_KYC", "PIECE_KYC", pieceId.toString(),
                Resultat.SUCCES);
        return new ContenuPiece(contenu, piece.typeMime());
    }

    /**
     * Décision de l'agent. L'approbation crée le lien de tutelle actif et active le compte ; rejet et demande
     * de complément exigent un motif, notifié au parent (US-PAR-001). Le SMS n'en reprend pas le contenu.
     */
    @Transactional
    public DossierInstruction decider(UUID agentId, UUID dossierId, Decision decision, String motif) {
        DossierKyc dossier = pourAgent(dossierId);
        Instant maintenant = horloge.instant();
        try {
            switch (decision) {
                case APPROUVER -> dossier.approuver(maintenant);
                case REJETER -> dossier.rejeter(motif, maintenant);
                case DEMANDER_COMPLEMENT -> dossier.demanderComplement(motif, maintenant);
            }
        } catch (TransitionIllegaleException erreur) {
            throw conflit();
        } catch (IllegalArgumentException erreur) {
            throw new ErreurMetier(CodeErreur.REQUETE_INVALIDE, "Indiquez le motif de la décision.");
        }

        Tuteur tuteur = (Tuteur) utilisateurs.findById(dossier.demandeurId()).orElseThrow();
        String texte;
        if (decision == Decision.APPROUVER) {
            liens.save(new LienTutelle(tuteur.id(), dossier.enfantId(), dossier.natureLien(), maintenant));
            tuteur.activer();
            evenements.publishEvent(new DossierKycApprouve(dossier.id(), tuteur.id(), dossier.enfantId()));
            texte = "FasoGuardian : votre compte est activé. Ouvrez l'application pour associer le bracelet.";
        } else if (decision == Decision.REJETER) {
            texte = "FasoGuardian : votre dossier n'a pas pu être validé. Ouvrez l'application pour en connaître le motif.";
        } else {
            texte = "FasoGuardian : un complément est nécessaire pour votre dossier. Ouvrez l'application.";
        }
        journal.consigner(agentId, RoleInterne.KYC.name(), "DECISION_KYC_" + dossier.statut().name(), "DOSSIER_KYC",
                dossierId.toString(), Resultat.SUCCES);
        sms.envoyer(new NumeroTelephone(chiffrement.dechiffrerTexte(CategorieDonnee.TELEPHONE, tuteur.telephoneChiffre())).e164(),
                texte);
        return vueInstruction(dossier);
    }

    // ------------------------------------------------------------------ vues

    /** Le parent n'accède qu'à ses propres dossiers ; un identifiant d'autrui est indiscernable d'un inconnu. */
    private DossierKyc duParent(UUID tuteurId, UUID dossierId) {
        DossierKyc dossier = dossiers.findById(dossierId).orElseThrow(InstructionKyc::introuvable);
        if (!dossier.demandeurId().equals(tuteurId)) {
            journal.consigner(tuteurId, Tuteur.ROLE, "ACCES_REFUSE", "DOSSIER_KYC", dossierId.toString(), Resultat.REFUS);
            throw introuvable();
        }
        return dossier;
    }

    private DossierKyc pourAgent(UUID dossierId) {
        return dossiers.findById(dossierId).filter(d -> d.statut() != Statut.BROUILLON)
                .orElseThrow(InstructionKyc::introuvable);
    }

    private DossierParent vueParent(DossierKyc d) {
        return new DossierParent(d.id(), d.reference(), d.statut(), d.canal(), d.natureLien(), d.motif(),
                d.deposeLe(), d.decideLe(),
                pieces.findByDossierId(d.id()).stream().map(InstructionKyc::vue).toList());
    }

    private DossierInstruction vueInstruction(DossierKyc d) {
        return new DossierInstruction(d.id(), d.reference(), d.statut(), d.canal(), d.natureLien(),
                dechiffrerJson(d.identiteChiffree(), IdentiteDeclaree.class),
                dechiffrerJson(d.enfantChiffre(), EnfantDeclare.class), d.motif(), d.deposeLe(),
                pieces.findByDossierId(d.id()).stream().map(InstructionKyc::vue).toList());
    }

    private static PieceVue vue(PieceJustificative p) {
        return new PieceVue(p.id(), p.type(), p.typeMime(), p.tailleOctets());
    }

    private byte[] chiffrerJson(Object valeur) {
        return chiffrement.chiffrer(CategorieDonnee.PIECE_KYC, json.writeValueAsBytes(valeur));
    }

    private <T> T dechiffrerJson(byte[] chiffre, Class<T> type) {
        return json.readValue(chiffrement.dechiffrer(CategorieDonnee.PIECE_KYC, chiffre), type);
    }

    /** Le contenu doit commencer par la signature du format annoncé : un exécutable renommé est refusé. */
    private static boolean signatureConforme(byte[] contenu, String typeMime) {
        byte[] attendu = switch (typeMime) {
            case "image/jpeg" -> new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
            case "image/png" -> new byte[] {(byte) 0x89, 'P', 'N', 'G'};
            default -> "%PDF-".getBytes(StandardCharsets.US_ASCII);
        };
        if (contenu.length < attendu.length) {
            return false;
        }
        for (int i = 0; i < attendu.length; i++) {
            if (contenu[i] != attendu[i]) {
                return false;
            }
        }
        return true;
    }

    private static String sha256(byte[] contenu) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenu));
        } catch (NoSuchAlgorithmException erreur) {
            throw new IllegalStateException(erreur);
        }
    }

    private static ErreurMetier introuvable() {
        return new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Dossier introuvable.");
    }

    private static ErreurMetier conflit() {
        return new ErreurMetier(CodeErreur.CONFLIT, "Cette action n'est pas possible dans l'état actuel du dossier.");
    }
}
