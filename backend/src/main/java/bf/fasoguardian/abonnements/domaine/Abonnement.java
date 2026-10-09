package bf.fasoguardian.abonnements.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Droit de service d'un enfant (FG-DOC-07 §4.5). En cas d'impayé, il se dégrade par étapes : rappel à J+1,
 * second rappel à J+8, restriction à J+15, jamais avant deux rappels (US-SYS-008). Le SOS, la détection de
 * retrait et la page publique ne dépendent pas de lui.
 */
@Entity
@Table(schema = "abonnements", name = "abonnement")
public class Abonnement {

    public static final int JOURS_AVANT_RAPPEL_D_ECHEANCE = 7;
    public static final int JOURS_AVANT_RAPPEL_1 = 1;
    public static final int JOURS_AVANT_RAPPEL_2 = 8;
    public static final int JOURS_AVANT_RESTRICTION = 15;
    /** Valeur de l'étape de relance une fois la restriction prononcée : il n'y a plus rien à relancer. */
    public static final int RELANCES_EPUISEES = 5;

    public enum Statut {
        /** Souscription commencée, premier paiement pas encore confirmé. */
        EN_ATTENTE,
        ACTIF,
        EN_RETARD,
        /** Géolocalisation continue et historique étendu suspendus. */
        RESTREINT
    }

    /** Ce que la relance du jour demande de faire. */
    public enum Relance {
        AUCUNE,
        RAPPEL_D_ECHEANCE,
        RENOUVELLEMENT,
        RAPPEL_1,
        RAPPEL_2,
        RESTRICTION
    }

    /** Période couverte par un paiement, début compris et fin exclue. */
    public record Periode(LocalDate debut, LocalDate fin) {
    }

    @Id
    private UUID id;

    @Column(name = "tuteur_id", nullable = false)
    private UUID tuteurId;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(name = "offre_code", nullable = false)
    private String offreCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(name = "prochaine_echeance")
    private LocalDate prochaineEcheance;

    @Column(name = "renouvellement_auto", nullable = false)
    private boolean renouvellementAuto;

    @Enumerated(EnumType.STRING)
    private Moyen moyen;

    @Column(name = "numero_chiffre")
    private byte[] numeroChiffre;

    @Column(name = "numero_masque")
    private String numeroMasque;

    @Column(name = "etape_relance", nullable = false)
    private int etapeRelance;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Version
    private long version;

    protected Abonnement() {
    }

    public Abonnement(UUID tuteurId, UUID enfantId, String offreCode, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.tuteurId = tuteurId;
        this.enfantId = enfantId;
        this.offreCode = offreCode;
        this.statut = Statut.EN_ATTENTE;
        this.creeLe = maintenant;
        this.modifieLe = maintenant;
    }

    /** Le tuteur qui paie, son portefeuille et son choix de renouvellement, tels qu'ils seront repris à l'échéance. */
    public void retenirPaiement(UUID payeur, Moyen moyenChoisi, byte[] numero, String masque, boolean auto, Instant maintenant) {
        this.tuteurId = payeur;
        this.moyen = moyenChoisi;
        this.numeroChiffre = numero;
        this.numeroMasque = masque;
        this.renouvellementAuto = auto;
        this.modifieLe = maintenant;
    }

    public void choisirRenouvellement(boolean auto, Instant maintenant) {
        this.renouvellementAuto = auto;
        this.modifieLe = maintenant;
    }

    /**
     * Un paiement est confirmé : l'offre payée s'applique, un mois s'ajoute à l'échéance en cours, ou part
     * d'aujourd'hui si elle était dépassée, et toute dégradation est levée.
     */
    public Periode confirmerPaiement(String offrePayee, LocalDate aujourdhui, Instant maintenant) {
        LocalDate debut = prochaineEcheance != null && !prochaineEcheance.isBefore(aujourdhui) ? prochaineEcheance : aujourdhui;
        this.offreCode = offrePayee;
        this.prochaineEcheance = debut.plusMonths(1);
        this.statut = Statut.ACTIF;
        this.etapeRelance = 0;
        this.modifieLe = maintenant;
        return new Periode(debut, prochaineEcheance);
    }

    /**
     * Relance quotidienne : rend au plus une étape, la plus ancienne qui soit due et pas encore faite. Un
     * traitement resté plusieurs jours sans tourner rattrape donc une étape par passage, et la restriction ne
     * vient jamais avant les deux rappels.
     */
    public Relance relancer(LocalDate aujourdhui, Instant maintenant) {
        if (prochaineEcheance == null) {
            return Relance.AUCUNE;
        }
        long jours = ChronoUnit.DAYS.between(prochaineEcheance, aujourdhui);
        Relance relance = Relance.AUCUNE;
        if (etapeRelance < 1 && jours >= -JOURS_AVANT_RAPPEL_D_ECHEANCE && jours < 0) {
            etapeRelance = 1;
            relance = Relance.RAPPEL_D_ECHEANCE;
        } else if (etapeRelance < 2 && jours >= 0) {
            etapeRelance = 2;
            relance = renouvellementAuto && moyen != null ? Relance.RENOUVELLEMENT : Relance.AUCUNE;
        } else if (etapeRelance < 3 && jours >= JOURS_AVANT_RAPPEL_1) {
            etapeRelance = 3;
            statut = Statut.EN_RETARD;
            relance = Relance.RAPPEL_1;
        } else if (etapeRelance < 4 && jours >= JOURS_AVANT_RAPPEL_2) {
            etapeRelance = 4;
            relance = Relance.RAPPEL_2;
        } else if (etapeRelance < RELANCES_EPUISEES && jours >= JOURS_AVANT_RESTRICTION) {
            etapeRelance = RELANCES_EPUISEES;
            statut = Statut.RESTREINT;
            relance = Relance.RESTRICTION;
        }
        if (relance != Relance.AUCUNE) {
            modifieLe = maintenant;
        }
        return relance;
    }

    /** Jour où les fonctions avancées seront suspendues si l'échéance en cours reste impayée. */
    public LocalDate restrictionLe() {
        return prochaineEcheance == null ? null : prochaineEcheance.plusDays(JOURS_AVANT_RESTRICTION);
    }

    public UUID id() {
        return id;
    }

    public UUID tuteurId() {
        return tuteurId;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public String offreCode() {
        return offreCode;
    }

    public Statut statut() {
        return statut;
    }

    public LocalDate prochaineEcheance() {
        return prochaineEcheance;
    }

    public boolean renouvellementAuto() {
        return renouvellementAuto;
    }

    public Moyen moyen() {
        return moyen;
    }

    public byte[] numeroChiffre() {
        return numeroChiffre;
    }

    public String numeroMasque() {
        return numeroMasque;
    }
}
