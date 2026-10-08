package bf.fasoguardian.identite.domaine;

import java.time.Instant;
import java.util.UUID;

import bf.fasoguardian.identite.domaine.DossierKyc.TypePiece;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Pièce déposée à l'appui d'un dossier KYC. Contenu chiffré, empreinte SHA-256 du clair pour l'intégrité. */
@Entity
@Table(schema = "identite", name = "piece_justificative")
public class PieceJustificative {

    @Id
    private UUID id;

    @Column(name = "dossier_id", nullable = false)
    private UUID dossierId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypePiece type;

    @Column(name = "contenu_chiffre", nullable = false)
    private byte[] contenuChiffre;

    @Column(name = "type_mime", nullable = false)
    private String typeMime;

    @Column(nullable = false)
    private String sha256;

    @Column(name = "taille_octets", nullable = false)
    private int tailleOctets;

    @Column(name = "deposee_le", nullable = false)
    private Instant deposeeLe;

    protected PieceJustificative() {
    }

    public PieceJustificative(UUID dossierId, TypePiece type, byte[] contenuChiffre, String typeMime, String sha256,
            int tailleOctets, Instant maintenant) {
        this.id = UUID.randomUUID();
        this.dossierId = dossierId;
        this.type = type;
        this.contenuChiffre = contenuChiffre;
        this.typeMime = typeMime;
        this.sha256 = sha256;
        this.tailleOctets = tailleOctets;
        this.deposeeLe = maintenant;
    }

    public UUID id() {
        return id;
    }

    public UUID dossierId() {
        return dossierId;
    }

    public TypePiece type() {
        return type;
    }

    public byte[] contenuChiffre() {
        return contenuChiffre;
    }

    public String typeMime() {
        return typeMime;
    }

    public String sha256() {
        return sha256;
    }

    public int tailleOctets() {
        return tailleOctets;
    }
}
