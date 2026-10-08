package bf.fasoguardian.identite.application;

import java.util.Set;

import bf.fasoguardian.identite.domaine.RoleInterne;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Crée le premier administrateur lorsque la base ne contient encore aucun agent et que ses identifiants
 * sont fournis par l'environnement ({@code FG_ADMIN_IDENTIFIANT}, {@code FG_ADMIN_MOT_DE_PASSE}).
 * Sans effet ensuite : ces variables peuvent être retirées après la première connexion, à laquelle
 * l'administrateur active son second facteur.
 */
@Component
class AmorcageAdministrateur implements ApplicationRunner {

    private static final Logger journal = LoggerFactory.getLogger(AmorcageAdministrateur.class);

    private final AgentsInternes agents;
    private final String identifiant;
    private final String motDePasse;

    AmorcageAdministrateur(AgentsInternes agents,
            @Value("${fasoguardian.amorcage.admin.identifiant:}") String identifiant,
            @Value("${fasoguardian.amorcage.admin.mot-de-passe:}") String motDePasse) {
        this.agents = agents;
        this.identifiant = identifiant;
        this.motDePasse = motDePasse;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (identifiant.isBlank() || motDePasse.isBlank() || !agents.aucunAgent()) {
            return;
        }
        agents.creer(null, identifiant, motDePasse, Set.of(RoleInterne.ADMIN));
        journal.info("Premier administrateur créé ; il activera son second facteur à sa première connexion");
    }
}
