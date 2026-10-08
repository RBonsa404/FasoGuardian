package bf.fasoguardian.identite.domaine;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Persistance d'un {@link CodeUsageUnique} : finalité, cible (empreinte du téléphone) et état des essais. */
@Entity
@Table(schema = "identite", name = "code_usage_unique")
public class CodeEmis {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String finalite;

    @Column(name = "cible_hash", nullable = false)
    private String cibleHash;

    @Column(nullable = false)
    private byte[] empreinte;

    @Column(name = "emis_le", nullable = false)
    private Instant emisLe;

    @Column(nullable = false)
    private int essais;

    @Column(nullable = false)
    private boolean consomme;

    protected CodeEmis() {
    }

    public CodeEmis(String finalite, String cibleHash, CodeUsageUnique code) {
        this.id = UUID.randomUUID();
        this.finalite = finalite;
        this.cibleHash = cibleHash;
        this.empreinte = code.empreinte();
        this.emisLe = code.emisLe();
    }

    public CodeUsageUnique.Resultat verifier(String saisie, Instant maintenant) {
        CodeUsageUnique code = code();
        CodeUsageUnique.Resultat resultat = code.verifier(contexte(finalite, cibleHash), saisie, maintenant);
        this.essais = code.essais();
        this.consomme = code.consomme();
        return resultat;
    }

    public CodeUsageUnique code() {
        return CodeUsageUnique.reconstituer(empreinte, emisLe, essais, consomme);
    }

    public static String contexte(String finalite, String cibleHash) {
        return finalite + ":" + cibleHash;
    }
}
