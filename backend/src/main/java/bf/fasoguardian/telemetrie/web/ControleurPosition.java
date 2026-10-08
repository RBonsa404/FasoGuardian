package bf.fasoguardian.telemetrie.web;

import java.util.UUID;

import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import bf.fasoguardian.telemetrie.application.Positions;
import bf.fasoguardian.telemetrie.application.Positions.Situation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** Dernière position connue de l'enfant et état de son bracelet (US-PAR-006). Jamais mise en cache. */
@RestController
@Tag(name = "Position")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('PARENT')")
class ControleurPosition {

    private final Positions positions;

    ControleurPosition(Positions positions) {
        this.positions = positions;
    }

    @Operation(summary = "Dernière position, avec son horodatage et sa précision, et état du bracelet (consultation journalisée)")
    @GetMapping("/api/v1/enfants/{enfantId}/position")
    ResponseEntity<Situation> situation(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        Situation situation = positions.situation(UUID.fromString(jeton.getSubject()), enfantId).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun bracelet n'est associé à cet enfant."));
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(situation);
    }
}
