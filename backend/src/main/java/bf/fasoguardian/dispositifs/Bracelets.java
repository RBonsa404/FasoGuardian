package bf.fasoguardian.dispositifs;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Registre des bracelets pour les autres modules : la télémétrie y vérifie qu'un appareil a le droit
 * d'émettre et retrouve l'enfant qui le porte.
 */
public interface Bracelets {

    /**
     * @param enfantId          enfant actuellement appairé, ou {@code null}
     * @param appaireDepuis     début de l'appairage en cours, ou {@code null} ; les données antérieures
     *                          appartiennent à un autre enfant et ne doivent jamais lui être montrées
     * @param accepteLesMessages faux si le bracelet n'est pas en service ou si son certificat est révoqué
     */
    record BraceletConnu(UUID id, String numeroSerie, UUID enfantId, Instant appaireDepuis,
            boolean accepteLesMessages) {
    }

    /** Bracelet désigné par son identifiant d'appareil, le numéro gravé (ADR 0009). */
    Optional<BraceletConnu> parNumeroSerie(String numeroSerie);

    /** Bracelet actuellement appairé à l'enfant. */
    Optional<BraceletConnu> deLEnfant(UUID enfantId);

    /**
     * Vérifie une signature du bracelet (ECDSA P-256 sur SHA-256, format brut R‖S) avec la clé publique relevée
     * sur son certificat. Faux si le bracelet est inconnu, hors service, sans clé enregistrée, ou si la signature
     * ne correspond pas.
     */
    boolean signatureValide(String numeroSerie, byte[] contenu, byte[] signature);

    /** @param intervalleS intervalle d'émission attendu en ce moment ; 0 si l'émission périodique est suspendue */
    record EnService(UUID braceletId, String numeroSerie, UUID enfantId, Instant appaireDepuis, int intervalleS) {
    }

    /** Bracelets actifs portés par un enfant, avec le rythme auquel ils doivent donner des nouvelles. */
    List<EnService> enService();

    /** @param fin fin de l'appairage, ou {@code null} s'il est en cours */
    record Periode(UUID braceletId, String numeroSerie, Instant debut, Instant fin) {
    }

    /** Tous les appairages de l'enfant, passés et en cours : les données d'un bracelet ne sont les siennes que sur ces périodes. */
    List<Periode> periodesDe(UUID enfantId);
}
