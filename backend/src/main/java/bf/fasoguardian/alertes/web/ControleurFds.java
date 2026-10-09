package bf.fasoguardian.alertes.web;

import java.util.UUID;

import bf.fasoguardian.alertes.application.AccusesFds;
import bf.fasoguardian.alertes.application.AccusesFds.Constat;
import bf.fasoguardian.alertes.application.Escalades.Dossier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Espace des forces de sécurité (US-FDS-001) : un signalement se retrouve par sa référence, jamais par liste. */
@RestController
@RequestMapping("/api/v1/console/fds/signalements")
@Tag(name = "Forces de sécurité")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('FDS')")
class ControleurFds {

    private final AccusesFds accuses;

    ControleurFds(AccusesFds accuses) {
        this.accuses = accuses;
    }

    @Operation(summary = "Signalement désigné par sa référence (consultation journalisée)")
    @GetMapping("/{reference}")
    ResponseEntity<Constat> constat(@AuthenticationPrincipal Jwt jeton, @PathVariable String reference) {
        return sansCache(accuses.constat(id(jeton), reference));
    }

    @Operation(summary = "Accuse réception du signalement : horodaté, journalisé, notifié aux parents")
    @PostMapping("/{reference}/accuse")
    ResponseEntity<Constat> accuser(@AuthenticationPrincipal Jwt jeton, @PathVariable String reference) {
        return sansCache(accuses.accuser(id(jeton), reference));
    }

    @Operation(summary = "Dossier transmis par la passerelle convenue, en PDF (téléchargement journalisé)")
    @GetMapping(value = "/{reference}/dossier", produces = MediaType.APPLICATION_PDF_VALUE)
    ResponseEntity<byte[]> dossier(@AuthenticationPrincipal Jwt jeton, @PathVariable String reference) {
        Dossier dossier = accuses.dossier(id(jeton), reference);
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + dossier.reference() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF).body(dossier.pdf());
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
