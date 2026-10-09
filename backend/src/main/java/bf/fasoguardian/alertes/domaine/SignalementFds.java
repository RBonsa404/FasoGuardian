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

/**
 * Dossier de signalement d'une alerte escaladée (FG-DOC-07 §4.4). Le dossier chiffré est effacé au bout de
 * 30 jours ; son empreinte et sa référence restent, pour prouver ce qui a été remis.
 */
@Entity
@Table(schema = "alertes", name = "signalement_fds")
public class SignalementFds {

    public static final Duration CONSERVATION_DU_DOSSIER = Duration.ofDays(30);

    public enum Canal {
        /** Aucune convention active : le parent remet lui-même le dossier aux autorités. */
        REMISE_PAR_LE_PARENT,
        PASSERELLE
    }

    @Id
    @Column(name = "alerte_id")
    private UUID alerteId;

    @Column(nullable = false)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Canal canal;

    @Column(name = "empreinte_dossier", nullable = false)
    private String empreinteDossier;

    @Column(name = "dossier_chiffre")
    private byte[] dossierChiffre;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "transmis_le")
    private Instant transmisLe;

    @Column(name = "accuse_le")
    private Instant accuseLe;

    @Column(name = "efface_le")
    private Instant effaceLe;

    protected SignalementFds() {
    }

    public SignalementFds(UUID alerteId, String reference, Canal canal, String empreinteDossier, byte[] dossierChiffre,
            Instant maintenant) {
        this.alerteId = alerteId;
        this.reference = reference;
        this.canal = canal;
        this.empreinteDossier = empreinteDossier;
        this.dossierChiffre = dossierChiffre;
        this.creeLe = maintenant;
        this.transmisLe = canal == Canal.PASSERELLE ? maintenant : null;
    }

    public void effacerDossier(Instant maintenant) {
        dossierChiffre = null;
        effaceLe = maintenant;
    }

    public UUID alerteId() {
        return alerteId;
    }

    public String reference() {
        return reference;
    }

    public Canal canal() {
        return canal;
    }

    public String empreinteDossier() {
        return empreinteDossier;
    }

    public byte[] dossierChiffre() {
        return dossierChiffre;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant disponibleJusquAu() {
        return creeLe.plus(CONSERVATION_DU_DOSSIER);
    }

    public boolean dossierDisponible() {
        return dossierChiffre != null;
    }
}
