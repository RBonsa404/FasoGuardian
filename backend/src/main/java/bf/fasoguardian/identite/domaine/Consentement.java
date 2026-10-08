package bf.fasoguardian.identite.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Trace horodatée d'un consentement accordé ou refusé, avec la version du texte présenté. Jamais modifiée. */
@Entity
@Table(schema = "identite", name = "consentement")
public class Consentement {

    @Id
    private UUID id;

    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeConsentement type;

    @Column(nullable = false)
    private boolean accorde;

    @Column(name = "version_texte", nullable = false)
    private String versionTexte;

    @Column(name = "enregistre_le", nullable = false)
    private Instant enregistreLe;

    protected Consentement() {
    }

    public Consentement(UUID utilisateurId, TypeConsentement type, boolean accorde, String versionTexte,
            Instant maintenant) {
        this.id = UUID.randomUUID();
        this.utilisateurId = utilisateurId;
        this.type = type;
        this.accorde = accorde;
        this.versionTexte = versionTexte;
        this.enregistreLe = maintenant;
    }

    public TypeConsentement type() {
        return type;
    }

    public boolean accorde() {
        return accorde;
    }
}
