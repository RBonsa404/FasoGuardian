package bf.fasoguardian.dispositifs.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.dispositifs.application.Maintenance;
import bf.fasoguardian.dispositifs.application.Maintenance.TicketVue;
import bf.fasoguardian.dispositifs.domaine.TicketMaintenance.Resolution;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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

    record DemandeRendezVous(@NotBlank @Size(max = Maintenance.POINT_RELAIS_MAX) String pointRelais, @NotNull Instant le) {
    }

    record DemandeRemplacement(@NotBlank @Size(max = 16) String numeroSerie) {
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

    @Operation(summary = "Sonde le bracelet par une localisation signée ; l'accusé dit s'il reçoit encore ses commandes")
    @PostMapping("/{ticketId}/diagnostic")
    ResponseEntity<TicketVue> diagnostiquer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID ticketId) {
        return sansCache(maintenance.diagnostiquer(id(jeton), ticketId));
    }

    @Operation(summary = "Propose au parent un rendez-vous en point relais, transmis par la plateforme")
    @PostMapping("/{ticketId}/rendez-vous")
    ResponseEntity<TicketVue> rendezVous(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID ticketId,
            @Valid @RequestBody DemandeRendezVous demande) {
        return sansCache(maintenance.fixerRendezVous(id(jeton), ticketId, demande.pointRelais(), demande.le()));
    }

    @Operation(summary = "Réserve l'unité en stock remise au parent ; son appairage par le parent clôt le ticket")
    @PostMapping("/{ticketId}/remplacement")
    ResponseEntity<TicketVue> remplacement(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID ticketId,
            @Valid @RequestBody DemandeRemplacement demande) {
        return sansCache(maintenance.reserverRemplacement(id(jeton), ticketId, demande.numeroSerie()));
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
