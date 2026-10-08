package bf.fasoguardian.dispositifs.domaine;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Crée un bracelet préparé à l'atelier avec la configuration par défaut de sa révision matérielle
 * (FG-DOC-07 §4.3 ; intervalles de FG-DOC-08 §6 : 5 min en usage normal, 60 s en alerte, 15 min en économie).
 */
public final class FabriqueBracelet {

    /** Intervalles par défaut en secondes : normal, alerte, économie. */
    private record Defauts(int normalS, int alerteS, int economieS) {
    }

    private static final Map<String, Defauts> REVISIONS = Map.of(
            "V0", new Defauts(300, 60, 900),
            "V1", new Defauts(300, 60, 900));

    /** Bracelet neuf et sa configuration, à enregistrer ensemble. */
    public record BraceletPrepare(Bracelet bracelet, ConfigurationBracelet configuration) {
    }

    private FabriqueBracelet() {
    }

    public static Set<String> revisionsConnues() {
        return REVISIONS.keySet();
    }

    public static BraceletPrepare preparer(String numeroSerie, byte[] imeiChiffre, String imeiEmpreinte,
            String revisionMaterielle, String versionLogiciel, String empreinteCertificat, String jetonQrSha256,
            String codeAppairageEmpreinte, Instant maintenant) {
        Defauts defauts = REVISIONS.get(revisionMaterielle);
        if (defauts == null) {
            throw new IllegalArgumentException("Révision matérielle inconnue : " + revisionMaterielle);
        }
        Bracelet bracelet = new Bracelet(numeroSerie, imeiChiffre, imeiEmpreinte, revisionMaterielle, versionLogiciel,
                empreinteCertificat, jetonQrSha256, codeAppairageEmpreinte, maintenant);
        return new BraceletPrepare(bracelet,
                new ConfigurationBracelet(bracelet.id(), defauts.normalS(), defauts.alerteS(), defauts.economieS()));
    }
}
