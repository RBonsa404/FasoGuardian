package bf.fasoguardian.alertes.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Fenêtre pendant laquelle le bracelet peut être retiré sans alerte (US-PAR-012) : de 15 minutes à 12 heures,
 * par pas de 15 minutes. Un bracelet retiré et non remis à l'échéance déclenche un rappel, puis une alerte.
 */
@Entity
@Table(schema = "alertes", name = "autorisation_retrait")
public class AutorisationRetrait {

    public static final Duration DUREE_MINIMALE = Duration.ofMinutes(15);
    public static final Duration DUREE_MAXIMALE = Duration.ofHours(12);
    public static final Duration PAS = Duration.ofMinutes(15);
    /** Le rappel part cinq minutes avant la fin de la fenêtre. */
    public static final Duration AVANCE_DU_RAPPEL = Duration.ofMinutes(5);

    public enum Motif {
        TOILETTE,
        RECHARGE,
        NUIT,
        AUTRE
    }

    public enum Statut {
        ACTIVE,
        /** Close par le parent ou par la remise du bracelet. */
        TERMINEE,
        /** Arrivée à son terme. */
        ECHUE
    }

    @Id
    private UUID id;

    @Column(name = "enfant_id", nullable = false)
    private UUID enfantId;

    @Column(name = "bracelet_id", nullable = false)
    private UUID braceletId;

    @Column(name = "accordee_par", nullable = false)
    private UUID accordeePar;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Motif motif;

    @Column(nullable = false)
    private Instant debut;

    @Column(nullable = false)
    private Instant fin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Statut statut;

    @Column(nullable = false)
    private boolean retire;

    @Column(name = "rappel_envoye", nullable = false)
    private boolean rappelEnvoye;

    @Column(name = "cloturee_le")
    private Instant clotureeLe;

    @Version
    private long version;

    protected AutorisationRetrait() {
    }

    public AutorisationRetrait(UUID enfantId, UUID braceletId, UUID accordeePar, Motif motif, Duration duree,
            Instant maintenant) {
        exigerDureeValide(duree);
        this.id = UUID.randomUUID();
        this.enfantId = enfantId;
        this.braceletId = braceletId;
        this.accordeePar = accordeePar;
        this.motif = motif;
        this.debut = maintenant;
        this.fin = maintenant.plus(duree);
        this.statut = Statut.ACTIVE;
    }

    /** Allonge la fenêtre ; sa durée totale reste bornée à 12 heures. */
    public void prolonger(Duration ajout) {
        exigerActive();
        Duration totale = Duration.between(debut, fin).plus(ajout);
        if (ajout.isNegative() || ajout.isZero() || ajout.toMinutes() % PAS.toMinutes() != 0
                || totale.compareTo(DUREE_MAXIMALE) > 0) {
            throw new IllegalArgumentException("La durée totale d'un retrait autorisé ne dépasse pas 12 heures");
        }
        fin = fin.plus(ajout);
        rappelEnvoye = false;
    }

    public boolean couvre(Instant instant) {
        return statut == Statut.ACTIVE && !instant.isBefore(debut) && instant.isBefore(fin);
    }

    /** Le bracelet a été retiré pendant la fenêtre : rien n'est signalé, le retrait est seulement noté. */
    public void noterRetrait() {
        exigerActive();
        retire = true;
    }

    /** Le bracelet est remis : la surveillance du retrait reprend aussitôt. */
    public void terminer(Instant maintenant) {
        exigerActive();
        statut = Statut.TERMINEE;
        clotureeLe = maintenant;
    }

    public boolean rappelDu(Instant maintenant) {
        return statut == Statut.ACTIVE && retire && !rappelEnvoye && !maintenant.isBefore(fin.minus(AVANCE_DU_RAPPEL));
    }

    public void noterRappel() {
        rappelEnvoye = true;
    }

    /**
     * La fenêtre est arrivée à son terme.
     *
     * @return {@code true} si le bracelet n'a pas été remis : c'est alors un retrait non autorisé
     */
    public boolean echoir(Instant maintenant) {
        exigerActive();
        statut = Statut.ECHUE;
        clotureeLe = maintenant;
        return retire;
    }

    private void exigerActive() {
        if (statut != Statut.ACTIVE) {
            throw new IllegalStateException("L'autorisation de retrait n'est plus active");
        }
    }

    private static void exigerDureeValide(Duration duree) {
        if (duree == null || duree.compareTo(DUREE_MINIMALE) < 0 || duree.compareTo(DUREE_MAXIMALE) > 0
                || duree.toMinutes() % PAS.toMinutes() != 0 || duree.toSecondsPart() != 0) {
            throw new IllegalArgumentException("Un retrait s'autorise pour 15 minutes à 12 heures, par pas de 15 minutes");
        }
    }

    public UUID id() {
        return id;
    }

    public UUID enfantId() {
        return enfantId;
    }

    public UUID braceletId() {
        return braceletId;
    }

    public Motif motif() {
        return motif;
    }

    public Instant debut() {
        return debut;
    }

    public Instant fin() {
        return fin;
    }

    public Statut statut() {
        return statut;
    }

    public boolean retire() {
        return retire;
    }
}
