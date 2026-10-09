package bf.fasoguardian.notifications.domaine;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Message adressé à un destinataire, suivi jusqu'à son accusé ou son repli par SMS. */
@Entity
@Table(schema = "notifications", name = "notification")
public class Notification {

    /** Sans accusé au bout de ce délai, une notification importante est doublée par SMS (US-ENF-001). */
    public static final Duration DELAI_AVANT_SMS = Duration.ofSeconds(60);

    public enum Urgence {
        CRITIQUE,
        IMPORTANTE,
        INFORMATION
    }

    @Id
    private UUID id;

    @Column(name = "destinataire_id", nullable = false)
    private UUID destinataireId;

    @Column(nullable = false)
    private String modele;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Urgence urgence;

    @Column(nullable = false)
    private String titre;

    @Column(nullable = false)
    private String texte;

    private String lien;

    private String reference;

    @Column(name = "creee_le", nullable = false)
    private Instant creeeLe;

    @Column(name = "poussee_le")
    private Instant pousseeLe;

    @Column(name = "accusee_le")
    private Instant accuseeLe;

    @Column(name = "sms_envoye_le")
    private Instant smsEnvoyeLe;

    protected Notification() {
    }

    public Notification(UUID destinataireId, String modele, Urgence urgence, String titre, String texte, String lien,
            String reference, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.destinataireId = destinataireId;
        this.modele = modele;
        this.urgence = urgence;
        this.titre = borner(titre, 80);
        this.texte = borner(texte, 240);
        this.lien = lien;
        this.reference = reference;
        this.creeeLe = maintenant;
    }

    public void noterPoussee(Instant maintenant) {
        pousseeLe = maintenant;
    }

    public void accuser(Instant maintenant) {
        if (accuseeLe == null) {
            accuseeLe = maintenant;
        }
    }

    public void noterSms(Instant maintenant) {
        smsEnvoyeLe = maintenant;
    }

    /** Vrai si le repli SMS reste à faire : importante, ni accusée ni déjà doublée, délai écoulé. */
    public boolean repliDu(Instant maintenant) {
        return urgence == Urgence.IMPORTANTE && accuseeLe == null && smsEnvoyeLe == null
                && !maintenant.isBefore(creeeLe.plus(DELAI_AVANT_SMS));
    }

    private static String borner(String valeur, int longueur) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException("Une notification a un titre et un texte");
        }
        String propre = valeur.strip();
        return propre.substring(0, Math.min(longueur, propre.length()));
    }

    public UUID id() {
        return id;
    }

    public String modele() {
        return modele;
    }

    public Instant creeeLe() {
        return creeeLe;
    }

    public UUID destinataireId() {
        return destinataireId;
    }

    public Urgence urgence() {
        return urgence;
    }

    public String titre() {
        return titre;
    }

    public String texte() {
        return texte;
    }

    public String lien() {
        return lien;
    }

    public boolean accusee() {
        return accuseeLe != null;
    }

    public boolean smsEnvoye() {
        return smsEnvoyeLe != null;
    }
}
