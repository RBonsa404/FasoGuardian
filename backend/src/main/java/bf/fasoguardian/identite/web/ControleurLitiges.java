package bf.fasoguardian.identite.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.identite.application.Litiges;
import bf.fasoguardian.identite.application.Litiges.LitigeVue;
import bf.fasoguardian.identite.domaine.Litige.Decision;
import bf.fasoguardian.identite.domaine.Litige.Fondement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Litiges de filiation, côté agent KYC (US-KYC-001). */
@RestController
@RequestMapping("/api/v1/console/kyc/litiges")
@Tag(name = "KYC")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('KYC')")
class ControleurLitiges {

    private final Litiges litiges;

    ControleurLitiges(Litiges litiges) {
        this.litiges = litiges;
    }

    record DemandeOuverture(@NotBlank @Size(max = 24) String dossierKyc, @NotBlank @Size(max = 500) String motif,
            boolean suspendreLaGeolocalisation) {
    }

    record DemandeSuspension(@NotNull Boolean suspendue) {
    }

    record DemandeDecision(@NotNull Decision decision, @NotNull Fondement fondement, @Size(max = 120) String referenceDuFondement) {
    }

    @Operation(summary = "Litiges, les plus récents d'abord")
    @GetMapping
    ResponseEntity<List<LitigeVue>> lister() {
        return sansCache(litiges.litiges());
    }

    @Operation(summary = "Un litige (consultation journalisée)")
    @GetMapping("/{litigeId}")
    ResponseEntity<LitigeVue> litige(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID litigeId) {
        return sansCache(litiges.litige(id(jeton), litigeId));
    }

    @Operation(summary = "Ouvre un litige sur le lien établi par un dossier KYC approuvé : réglages gelés, parties prévenues")
    @PostMapping
    ResponseEntity<LitigeVue> ouvrir(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeOuverture demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(litiges.ouvrir(id(jeton), demande.dossierKyc(), demande.motif(), demande.suspendreLaGeolocalisation()));
    }

    @Operation(summary = "Pose ou lève la suspension conservatoire de la géolocalisation")
    @PutMapping("/{litigeId}/geolocalisation")
    ResponseEntity<LitigeVue> suspendre(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID litigeId,
            @Valid @RequestBody DemandeSuspension demande) {
        return sansCache(litiges.suspendreLaGeolocalisation(id(jeton), litigeId, demande.suspendue()));
    }

    @Operation(summary = "Enregistre la décision : elle est appliquée, journalisée et notifiée aux parties")
    @PostMapping("/{litigeId}/decision")
    ResponseEntity<LitigeVue> decider(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID litigeId,
            @Valid @RequestBody DemandeDecision demande) {
        return sansCache(litiges.decider(id(jeton), litigeId, demande.decision(), demande.fondement(), demande.referenceDuFondement()));
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
