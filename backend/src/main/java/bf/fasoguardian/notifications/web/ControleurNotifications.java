package bf.fasoguardian.notifications.web;

import java.util.Map;
import java.util.List;
import java.util.UUID;

import bf.fasoguardian.notifications.application.ServiceNotifications;
import bf.fasoguardian.notifications.application.ServiceNotifications.Recue;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Abonnement d'un navigateur aux notifications push et accusé de livraison. */
@RestController
@Tag(name = "Notifications")
class ControleurNotifications {

    private final ServiceNotifications notifications;

    ControleurNotifications(ServiceNotifications notifications) {
        this.notifications = notifications;
    }

    record ClesDto(@NotBlank @Size(max = 128) String p256dh, @NotBlank @Size(max = 64) String auth) {
    }

    record DemandeAbonnement(@NotBlank @Size(max = 1024) String endpoint, @NotNull @Valid ClesDto keys) {
    }

    record DemandeDesabonnement(@NotBlank @Size(max = 1024) String endpoint) {
    }

    @Operation(summary = "Mes dernières notifications, hors alertes")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/v1/notifications")
    ResponseEntity<List<Recue>> recues(@AuthenticationPrincipal Jwt jeton) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(notifications.recues(UUID.fromString(jeton.getSubject())));
    }

    @Operation(summary = "Clé publique du serveur d'application, à passer à PushManager.subscribe")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/v1/notifications/cle-publique")
    ResponseEntity<Map<String, String>> clePublique() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("clePublique", notifications.clePubliquePush()));
    }

    @Operation(summary = "Abonne ce navigateur ; le corps est celui de PushSubscription.toJSON()")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/api/v1/notifications/abonnements")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void abonner(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeAbonnement demande) {
        notifications.abonner(UUID.fromString(jeton.getSubject()), demande.endpoint(), demande.keys().p256dh(), demande.keys().auth());
    }

    @Operation(summary = "Désabonne ce navigateur")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("isAuthenticated()")
    @PostMapping("/api/v1/notifications/desabonnement")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void desabonner(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeDesabonnement demande) {
        notifications.desabonner(UUID.fromString(jeton.getSubject()), demande.endpoint());
    }

    /**
     * Appelé par le service worker, qui n'a pas de session : l'identifiant de la notification, aléatoire et
     * transmis chiffré au seul navigateur abonné, tient lieu de preuve. La réponse est la même qu'il existe ou non.
     */
    @Operation(summary = "Accusé de livraison d'une notification push")
    @PostMapping("/api/v1/public/notifications/{notificationId}/accuse")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void accuser(@PathVariable UUID notificationId) {
        notifications.accuserLivraison(notificationId);
    }
}
