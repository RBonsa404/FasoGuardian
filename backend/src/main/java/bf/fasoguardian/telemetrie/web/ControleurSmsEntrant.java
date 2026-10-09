package bf.fasoguardian.telemetrie.web;

import bf.fasoguardian.telemetrie.application.ReceptionSms;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Point d'entrée de la passerelle SMS (US-SYS-001). Elle n'a pas de session : le sceau de son appel tient lieu
 * d'authentification, puis chaque SMS est vérifié avec la clé du bracelet qui l'a signé.
 */
@RestController
@Tag(name = "Télémétrie")
class ControleurSmsEntrant {

    private final ReceptionSms reception;

    ControleurSmsEntrant(ReceptionSms reception) {
        this.reception = reception;
    }

    /** La réponse est la même que le SMS soit accepté ou rejeté : la passerelle n'a rien à en apprendre. */
    @Operation(summary = "SMS de repli d'un bracelet, remis par la passerelle")
    @PostMapping("/api/v1/public/sms/entrant")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recevoir(@RequestHeader(name = "X-FG-Signature", required = false) String signature, @RequestBody byte[] corps) {
        reception.recevoir(signature, corps);
    }
}
