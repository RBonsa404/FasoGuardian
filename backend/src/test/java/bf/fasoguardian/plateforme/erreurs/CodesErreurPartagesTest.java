package bf.fasoguardian.plateforme.erreurs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Les applications ne lisent le message d'une erreur que si elles en connaissent le code : un code publié par
 * le serveur et absent du client s'afficherait « Une erreur est survenue » au lieu de dire ce qui se passe.
 */
class CodesErreurPartagesTest {

    private static final Path CLIENT = Path.of("..", "frontend", "projects", "api", "src", "lib", "probleme.ts");

    @Test
    void chaqueCodeDuServeurEstConnuDuClient() throws IOException {
        String client = Files.readString(CLIENT);
        assertThat(Arrays.stream(CodeErreur.values()).map(Enum::name).filter(code -> !client.contains("'" + code + "',"))).isEmpty();
    }
}
