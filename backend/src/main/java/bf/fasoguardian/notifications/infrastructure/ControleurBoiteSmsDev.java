package bf.fasoguardian.notifications.infrastructure;

import java.util.List;

import bf.fasoguardian.notifications.infrastructure.SmsBacASable.SmsEnvoye;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Boîte de réception de l'adaptateur SMS « bac à sable », pour lire en développement et dans les tests de
 * bout en bout les codes qui partiraient par SMS. N'existe que sous le profil {@code dev} et seulement si
 * l'adaptateur bac à sable est actif : jamais en recette ni en production.
 */
@RestController
@RequestMapping("/api/v1/dev/sms")
@Profile("dev")
@ConditionalOnProperty(name = "fasoguardian.sms.adaptateur", havingValue = "bac-a-sable")
@Hidden
class ControleurBoiteSmsDev {

    private final SmsBacASable boite;

    ControleurBoiteSmsDev(SmsBacASable boite) {
        this.boite = boite;
    }

    @GetMapping
    List<SmsEnvoye> messages() {
        return boite.tous();
    }
}
