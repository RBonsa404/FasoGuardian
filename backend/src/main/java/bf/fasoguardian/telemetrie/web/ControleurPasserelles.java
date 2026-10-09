package bf.fasoguardian.telemetrie.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.telemetrie.application.PasserellesLorawan;
import bf.fasoguardian.telemetrie.application.PasserellesLorawan.Vue;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Passerelles LoRaWAN (US-SYS-004) : registre tenu par l'administrateur, et point d'entrée du serveur de réseau,
 * qui n'a pas de session et s'authentifie par le sceau de son appel.
 */
@RestController
@Tag(name = "Télémétrie")
class ControleurPasserelles {

    private static final String REGISTRE = "/api/v1/console/passerelles";

    record Installation(@NotBlank @Pattern(regexp = "\\s*[0-9A-Fa-f]{16}\\s*") String eui, @NotBlank @Size(max = 80) String etablissement,
            @NotNull @DecimalMin("-90") @DecimalMax("90") Double latitude, @NotNull @DecimalMin("-180") @DecimalMax("180") Double longitude,
            @NotNull @Min(20) @Max(2000) Integer rayonM) {
    }

    private final PasserellesLorawan passerelles;

    ControleurPasserelles(PasserellesLorawan passerelles) {
        this.passerelles = passerelles;
    }

    @Operation(summary = "Passerelles LoRaWAN en service et leur état")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping(REGISTRE)
    List<Vue> enService() {
        return passerelles.enService();
    }

    @Operation(summary = "Enregistre une passerelle et l'enceinte qu'elle couvre")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping(REGISTRE)
    @ResponseStatus(HttpStatus.CREATED)
    Vue installer(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody Installation demande) {
        return passerelles.installer(UUID.fromString(jeton.getSubject()), demande.eui(), demande.etablissement(), demande.latitude(),
                demande.longitude(), demande.rayonM());
    }

    @Operation(summary = "Retire une passerelle : ses trames ne sont plus acceptées")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping(REGISTRE + "/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void retirer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID id) {
        passerelles.retirer(UUID.fromString(jeton.getSubject()), id);
    }

    /** La réponse est la même que la trame soit acceptée ou rejetée : le serveur de réseau n'a rien à en apprendre. */
    @Operation(summary = "Trame d'un bracelet entendue par une passerelle, remise par le serveur de réseau LoRaWAN")
    @PostMapping("/api/v1/public/lorawan/trames")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void recevoir(@RequestHeader(name = "X-FG-Signature", required = false) String signature, @RequestBody byte[] corps) {
        passerelles.recevoir(signature, corps);
    }
}
