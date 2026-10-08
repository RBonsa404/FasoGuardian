package bf.fasoguardian.famille.domaine;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Fiche d'identité de l'enfant. Aucun compte propre : elle n'est accessible qu'au travers d'un lien de
 * tutelle actif (module identite). Créée à l'approbation du dossier KYC, sous l'identifiant qui y figure.
 */
@Entity
@Table(schema = "famille", name = "enfant")
public class Enfant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private String nom;

    @Column(name = "date_naissance", nullable = false)
    private LocalDate dateNaissance;

    @Column(name = "photo_ref")
    private String photoRef;

    @Column(name = "profil_chiffre")
    private byte[] profilChiffre;

    @Column(name = "cree_le", nullable = false)
    private Instant creeLe;

    @Column(name = "modifie_le", nullable = false)
    private Instant modifieLe;

    @Version
    private long version;

    protected Enfant() {
    }

    public Enfant(UUID id, String prenom, String nom, LocalDate dateNaissance, Instant maintenant) {
        this.id = id;
        this.prenom = prenom;
        this.nom = nom;
        this.dateNaissance = dateNaissance;
        this.creeLe = maintenant;
        this.modifieLe = maintenant;
    }

    /** Applique les nouvelles valeurs et renvoie les champs réellement modifiés, pour l'historique. */
    public List<String> mettreAJour(String nouveauPrenom, String nouveauNom, Instant maintenant) {
        List<String> modifies = new ArrayList<>();
        if (nouveauPrenom != null && !nouveauPrenom.isBlank() && !nouveauPrenom.trim().equals(prenom)) {
            prenom = nouveauPrenom.trim();
            modifies.add("prenom");
        }
        if (nouveauNom != null && !nouveauNom.isBlank() && !nouveauNom.trim().equals(nom)) {
            nom = nouveauNom.trim();
            modifies.add("nom");
        }
        if (!modifies.isEmpty()) {
            modifieLe = maintenant;
        }
        return modifies;
    }

    public void definirProfil(byte[] nouveauProfilChiffre, Instant maintenant) {
        this.profilChiffre = nouveauProfilChiffre;
        this.modifieLe = maintenant;
    }

    public byte[] profilChiffre() {
        return profilChiffre;
    }

    public UUID id() {
        return id;
    }

    public String prenom() {
        return prenom;
    }

    public String nom() {
        return nom;
    }

    public LocalDate dateNaissance() {
        return dateNaissance;
    }

    public Instant creeLe() {
        return creeLe;
    }

    public Instant modifieLe() {
        return modifieLe;
    }
}
