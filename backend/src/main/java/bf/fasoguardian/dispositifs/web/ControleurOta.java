package bf.fasoguardian.dispositifs.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.dispositifs.application.CampagnesOta;
import bf.fasoguardian.dispositifs.application.CampagnesOta.CampagneVue;
import bf.fasoguardian.dispositifs.application.CampagnesOta.Image;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Campagnes de mise à jour du logiciel embarqué (écran 68), ouvertes au service après-vente et à l'administrateur. */
@RestController
@RequestMapping("/api/v1/console/sav/ota")
@Tag(name = "Service après-vente")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasAnyRole('SAV', 'ADMIN')")
class ControleurOta {

    record DemandeImage(@NotBlank @Pattern(regexp = "\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}") String version,
            @NotBlank @Size(max = 300) @Pattern(regexp = "https://\\S+") String urlImage,
            @NotNull @Min(1) @Max(16 * 1024 * 1024) Long tailleOctets, @NotBlank @Pattern(regexp = "[0-9a-fA-F]{64}") String sha256,
            @NotBlank @Size(max = 128) String signature, @Size(max = 200) String note) {
    }

    private final CampagnesOta campagnes;

    ControleurOta(CampagnesOta campagnes) {
        this.campagnes = campagnes;
    }

    @Operation(summary = "Campagnes de mise à jour et avancement de leurs vagues")
    @GetMapping
    List<CampagneVue> campagnes() {
        return campagnes.campagnes();
    }

    @Operation(summary = "Enregistre une image signée à déployer ; une signature invalide est refusée et journalisée")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CampagneVue preparer(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeImage demande) {
        return campagnes.preparer(id(jeton), new Image(demande.version(), demande.urlImage(), demande.tailleOctets(), demande.sha256(),
                demande.signature(), demande.note()));
    }

    @Operation(summary = "Lance la vague suivante : demande signée aux bracelets visés, parents informés")
    @PostMapping("/{campagneId}/vagues")
    CampagneVue lancer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID campagneId) {
        return campagnes.lancerLaVagueSuivante(id(jeton), campagneId);
    }

    @Operation(summary = "Met la campagne en pause : plus aucune demande ne part")
    @PostMapping("/{campagneId}/pause")
    CampagneVue pause(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID campagneId) {
        return campagnes.mettreEnPause(id(jeton), campagneId);
    }

    @Operation(summary = "Reprend une campagne en pause")
    @PostMapping("/{campagneId}/reprise")
    CampagneVue reprise(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID campagneId) {
        return campagnes.reprendre(id(jeton), campagneId);
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }
}
