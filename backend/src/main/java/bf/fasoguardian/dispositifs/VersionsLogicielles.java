package bf.fasoguardian.dispositifs;

import java.util.UUID;

/** Ce que la télémétrie apprend du logiciel embarqué d'un bracelet (US-PAR-013). */
public interface VersionsLogicielles {

    /** Le bracelet annonce la version qu'il exécute : le parc est tenu à jour et la campagne en cours avance. */
    void versionConstatee(UUID braceletId, String version);
}
