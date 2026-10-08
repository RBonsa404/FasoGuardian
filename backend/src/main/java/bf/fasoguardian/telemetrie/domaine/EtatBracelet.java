package bf.fasoguardian.telemetrie.domaine;

import java.time.Instant;

/** Dernier état connu du bracelet, remplacé à chaque message ; chaque champ peut manquer. */
public record EtatBracelet(Integer batterie, Integer signalDbm, String reseau, String operateur, Boolean enMouvement,
        Boolean enLigne, String versionLogiciel, Instant dernierContact) {
}
