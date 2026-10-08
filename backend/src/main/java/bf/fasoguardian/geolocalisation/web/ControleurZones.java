package bf.fasoguardian.geolocalisation.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.geolocalisation.application.SafeZones;
import bf.fasoguardian.geolocalisation.application.SafeZones.Forme;
import bf.fasoguardian.geolocalisation.application.SafeZones.Point;
import bf.fasoguardian.geolocalisation.application.SafeZones.SaisieZone;
import bf.fasoguardian.geolocalisation.application.SafeZones.ZoneVue;
import bf.fasoguardian.geolocalisation.application.SafeZones.ZonesVue;
import bf.fasoguardian.geolocalisation.domaine.SafeZone.Categorie;
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

/** Safe Zones d'un enfant (US-PAR-007). Les actions sensibles portent le code du second facteur MODIFIER_SAFE_ZONE. */
@RestController
@RequestMapping("/api/v1/enfants/{enfantId}/zones")
@Tag(name = "Safe Zones")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('PARENT')")
class ControleurZones {

    private final SafeZones zones;

    ControleurZones(SafeZones zones) {
        this.zones = zones;
    }

    record PointDto(@NotNull Double latitude, @NotNull Double longitude) {
    }

    record DemandeZone(@NotNull Forme forme, @NotBlank @Size(max = 40) String nom, @NotNull Categorie categorie,
            @Valid PointDto centre, Integer rayonM, @Valid @Size(max = 20) List<PointDto> sommets,
            @NotNull @Size(min = 1, max = 7) List<Integer> jours, @NotBlank @Size(max = 5) String debut,
            @NotBlank @Size(max = 5) String fin, @NotNull Integer toleranceS, @Size(max = 6) String codeSecondFacteur) {
    }

    record DemandeConfirmee(@Size(max = 6) String codeSecondFacteur) {
    }

    @Operation(summary = "Zones de l'enfant, leur état du moment et le nombre permis par l'offre")
    @GetMapping
    ResponseEntity<ZonesVue> zones(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(zones.zones(id(jeton), enfantId));
    }

    @Operation(summary = "Crée une zone circulaire ou polygonale, active dès le début de sa plage horaire")
    @PostMapping
    ResponseEntity<ZoneVue> creer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeZone demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(zones.creer(id(jeton), enfantId, saisie(demande), demande.codeSecondFacteur()));
    }

    @Operation(summary = "Modifie le périmètre, la plage horaire ou le délai de tolérance")
    @PutMapping("/{zoneId}")
    ResponseEntity<ZoneVue> modifier(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @PathVariable UUID zoneId, @Valid @RequestBody DemandeZone demande) {
        return sansCache(zones.modifier(id(jeton), enfantId, zoneId, saisie(demande), demande.codeSecondFacteur()));
    }

    @Operation(summary = "Suspend la zone en conservant sa configuration")
    @PostMapping("/{zoneId}/suspension")
    ResponseEntity<ZoneVue> suspendre(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @PathVariable UUID zoneId, @Valid @RequestBody DemandeConfirmee demande) {
        return sansCache(zones.suspendre(id(jeton), enfantId, zoneId, demande.codeSecondFacteur()));
    }

    @Operation(summary = "Réactive une zone suspendue")
    @PostMapping("/{zoneId}/reactivation")
    ResponseEntity<ZoneVue> reactiver(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @PathVariable UUID zoneId) {
        return sansCache(zones.reactiver(id(jeton), enfantId, zoneId));
    }

    @Operation(summary = "Supprime la zone")
    @PostMapping("/{zoneId}/suppression")
    ResponseEntity<Void> supprimer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @PathVariable UUID zoneId, @Valid @RequestBody DemandeConfirmee demande) {
        zones.supprimer(id(jeton), enfantId, zoneId, demande.codeSecondFacteur());
        return ResponseEntity.noContent().build();
    }

    private static SaisieZone saisie(DemandeZone d) {
        return new SaisieZone(d.forme(), d.nom(), d.categorie(), point(d.centre()), d.rayonM(),
                d.sommets() == null ? null : d.sommets().stream().map(ControleurZones::point).toList(), d.jours(),
                d.debut(), d.fin(), d.toleranceS());
    }

    private static Point point(PointDto point) {
        return point == null ? null : new Point(point.latitude(), point.longitude());
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
