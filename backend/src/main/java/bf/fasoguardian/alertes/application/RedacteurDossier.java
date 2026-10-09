package bf.fasoguardian.alertes.application;

import java.time.Instant;
import java.util.List;

import bf.fasoguardian.famille.DossiersEnfants.Identification;
import bf.fasoguardian.telemetrie.TrajetsRecents.Point;

/** Met en page le dossier de signalement. */
public interface RedacteurDossier {

    /**
     * @param typeAlerte   nature de l'alerte à l'origine du signalement, en clair
     * @param journal      lignes horodatées du journal de l'alerte
     * @param trajet       positions des deux dernières heures, dans l'ordre chronologique
     */
    record Contenu(String reference, Instant etabliLe, Identification enfant, String typeAlerte, Instant alerteOuverteLe,
            List<String> journal, List<Point> trajet) {
    }

    byte[] rediger(Contenu contenu);
}
