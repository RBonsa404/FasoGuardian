package bf.fasoguardian.plateforme.chiffrement;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * La configuration livrée doit déclarer une clé pour chaque catégorie de donnée chiffrée : les tests
 * d'intégration tirent leurs clés au hasard et ne verraient pas un oubli, qui empêcherait le serveur de
 * démarrer en production.
 */
class ConfigurationClesTest {

    @Test
    void chaqueCategorieDeDonneeAUneCleDeclareeParVariableDEnvironnement() {
        YamlPropertiesFactoryBean lecteur = new YamlPropertiesFactoryBean();
        lecteur.setResources(new ClassPathResource("application.yml"));
        Properties proprietes = lecteur.getObject();

        for (CategorieDonnee categorie : CategorieDonnee.values()) {
            // Selon la version du lecteur YAML, la version de clé « 1 » est rendue en indice ou en propriété.
            String valeur = proprietes.getProperty("fasoguardian.chiffrement.cles." + categorie + "[1]",
                    proprietes.getProperty("fasoguardian.chiffrement.cles." + categorie + ".1"));
            assertThat(valeur).as("clé de la catégorie %s dans application.yml", categorie)
                    .isEqualTo("${FG_CLE_" + categorie + ":}");
        }
        assertThat(proprietes.getProperty("fasoguardian.chiffrement.cle-empreinte")).isEqualTo("${FG_CLE_EMPREINTE:}");
    }
}
