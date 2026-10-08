package bf.fasoguardian.identite.domaine;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Instruction du lien de filiation ou de tutelle. Cycle : BROUILLON → DEPOSE → EN_INSTRUCTION →
 * APPROUVE / REJETE / COMPLEMENT_DEMANDE (puis nouveau dépôt). Le brouillon précède le cycle de FG-DOC-07 :
 * il porte le dossier pendant que le parent ajoute ses pièces. Un dossier approuvé crée un lien de tutelle actif.
 */
@Entity
@Table(schema = "identite", name = "dossier_kyc")
public class DossierKyc {

    public enum Statut {
        BROUILLON,
        DEPOSE,
        EN_INSTRUCTION,
        COMPLEMENT_DEMANDE,
        APPROUVE,
        REJETE;

        public boolean clos() {
            return this == APPROUVE || this == REJETE;
        }
    }

    public enum Canal {
        EN_LIGNE,
        POINT_INSCRIPTION
    }

    public enum NatureLien {
        PARENT,
        TUTEUR
    }

    public enum TypePiece {
        PIECE_RECTO,
        PIECE_VERSO,
        ACTE_NAISSANCE,
        JUGEMENT_TUTELLE
    }

    @Id
    private UUID id;

    @Column(nullable = false)
    private String reference;

    @Column(name = "demandeur_id", nullable = false)
    private UUID demandeurId;

    @Column(name = "agent_id")
    private UUID agentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Canal canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "nature_lien", nullable = false)
    private NatureLien natureLien;

    @Column(name = "identite_chiffree", nullable = false)
    private byte[] identiteChiffree;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(name = "enfant_chiffre", nullable = false)
    private byte[] enfantChiffre;

    @Column
    private String motif;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "depose_le")
    private Instant deposeLe;

    @Column(name = "decide_le")
    private Instant decideLe;

    @Version
    private long version;

    protected DossierKyc() {
    }

    public DossierKyc(String reference, UUID demandeurId, Canal canal, NatureLien natureLien, byte[] identiteChiffree,
            byte[] enfantChiffre, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.reference = reference;
        this.demandeurId = demandeurId;
        this.canal = canal;
        this.natureLien = natureLien;
        this.identiteChiffree = identiteChiffree;
        this.enfantId = UUID.randomUUID();
        this.enfantChiffre = enfantChiffre;
        this.statut = Statut.BROUILLON;
        this.creeLe = maintenant;
    }

    /** Les pièces ne s'ajoutent qu'avant le dépôt ou en réponse à une demande de complément. */
    public boolean piecesModifiables() {
        return statut == Statut.BROUILLON || statut == Statut.COMPLEMENT_DEMANDE;
    }

    /**
     * Dépôt en ligne : recto de la pièce d'identité et justificatif du lien exigés (jugement de tutelle pour un
     * tuteur, acte de naissance pour un parent). En point d'inscription, l'agent examine les originaux.
     */
    public void deposer(Set<TypePiece> piecesPresentes, Instant maintenant) {
        exiger(piecesModifiables());
        if (canal == Canal.EN_LIGNE) {
            TypePiece justificatif = natureLien == NatureLien.TUTEUR ? TypePiece.JUGEMENT_TUTELLE : TypePiece.ACTE_NAISSANCE;
            if (!piecesPresentes.contains(TypePiece.PIECE_RECTO) || !piecesPresentes.contains(justificatif)) {
                throw new DossierIncompletException();
            }
        }
        this.statut = Statut.DEPOSE;
        this.deposeLe = maintenant;
        this.motif = null;
    }

    public void prendreEnCharge(UUID agent) {
        exiger(statut == Statut.DEPOSE);
        this.statut = Statut.EN_INSTRUCTION;
        this.agentId = agent;
    }

    public void approuver(Instant maintenant) {
        decider(Statut.APPROUVE, null, maintenant);
    }

    public void rejeter(String motifRejet, Instant maintenant) {
        decider(Statut.REJETE, exigerMotif(motifRejet), maintenant);
    }

    public void demanderComplement(String motifComplement, Instant maintenant) {
        decider(Statut.COMPLEMENT_DEMANDE, exigerMotif(motifComplement), maintenant);
    }

    private void decider(Statut decision, String motifDecision, Instant maintenant) {
        exiger(statut == Statut.EN_INSTRUCTION);
        this.statut = decision;
        this.motif = motifDecision;
        this.decideLe = maintenant;
    }

    private static String exigerMotif(String motif) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Un motif est obligatoire");
        }
        return motif.trim();
    }

    private void exiger(boolean condition) {
        if (!condition) {
            throw new TransitionIllegaleException(statut);
        }
    }

    public UUID id() {
        return id;
    }

    public String reference() {
        return reference;
    }

    public UUID demandeurId() {
        return demandeurId;
    }

    public UUID agentId() {
        return agentId;
    }

    public Statut statut() {
        return statut;
    }

    public Canal canal() {
        return canal;
    }

    public NatureLien natureLien() {
        return natureLien;
    }

    public byte[] identiteChiffree() {
        return identiteChiffree;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public byte[] enfantChiffre() {
        return enfantChiffre;
    }

    public String motif() {
        return motif;
    }

    public Instant deposeLe() {
        return deposeLe;
    }

    public Instant decideLe() {
        return decideLe;
    }

    public static class TransitionIllegaleException extends IllegalStateException {
        public TransitionIllegaleException(Statut statut) {
            super("Action impossible sur un dossier au statut " + statut);
        }
    }

    public static class DossierIncompletException extends IllegalStateException {
        public DossierIncompletException() {
            super("Pièces obligatoires manquantes");
        }
    }
}
