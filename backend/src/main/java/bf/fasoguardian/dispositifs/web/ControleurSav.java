package bf.fasoguardian.dispositifs.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.dispositifs.application.Maintenance;
import bf.fasoguardian.dispositifs.application.Maintenance.TicketVue;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance.Resolution;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tickets de maintenance du service après-vente (US-SAV-001) : état des bracelets seulement. */
@RestController
@RequestMapping("/api/v1/console/sav/tickets")
@Tag(name = "Service après-vente")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('SAV')")
class ControleurSav {

    private final Maintenance maintenance;

    ControleurSav(Maintenance maintenance) {
        this.maintenance = maintenance;
    }

    record DemandeResolution(@NotNull Resolution resolution, @Size(max = 300) String note) {
    }

    @Operation(summary = "Tickets à traiter, les plus anciens d'abord ; « resolus » donne les cent derniers résolus")
    @GetMapping
    ResponseEntity<List<TicketVue>> tickets(@AuthenticationPrincipal Jwt jeton, @RequestParam(defaultValue = "false") boolean resolus) {
        return sansCache(maintenance.tickets(id(jeton), resolus));
    }

    @Operation(summary = "Prend le ticket en charge")
    @PostMapping("/{ticketId}/prise-en-charge")
    ResponseEntity<TicketVue> prendreEnCharge(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID ticketId) {
        return sansCache(maintenance.prendreEnCharge(id(jeton), ticketId));
    }

    @Operation(summary = "Clôt le ticket en disant comment il a été résolu")
    @PostMapping("/{ticketId}/resolution")
    ResponseEntity<TicketVue> resoudre(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID ticketId,
            @Valid @RequestBody DemandeResolution demande) {
        return sansCache(maintenance.resoudre(id(jeton), ticketId, demande.resolution(), demande.note()));
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
