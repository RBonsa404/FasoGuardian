package bf.fasoguardian.identite.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.identite.application.Litiges;
import bf.fasoguardian.identite.application.Litiges.LitigeDuTuteur;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Litige de filiation vu du parent (US-KYC-001) : l'application explique pourquoi les réglages de l'enfant sont
 * gelés, au lieu de le découvrir au premier refus.
 */
@RestController
@Tag(name = "Compte")
@SecurityRequirement(name = "jetonAcces")
class ControleurMesuresConservatoires {

    private final Litiges litiges;

    ControleurMesuresConservatoires(Litiges litiges) {
        this.litiges = litiges;
    }

    @Operation(summary = "Litiges ouverts sur mes liens de tutelle : enfant concerné et mesures en vigueur")
    @PreAuthorize("hasRole('PARENT')")
    @GetMapping("/api/v1/moi/litiges")
    ResponseEntity<List<LitigeDuTuteur>> mesLitiges(@AuthenticationPrincipal Jwt jeton) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(litiges.enCoursPour(UUID.fromString(jeton.getSubject())));
    }
}
