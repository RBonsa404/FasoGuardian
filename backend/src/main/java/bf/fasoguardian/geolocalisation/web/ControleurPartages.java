package bf.fasoguardian.geolocalisation.web;

import java.util.UUID;

import bf.fasoguardian.geolocalisation.application.Partages;
import bf.fasoguardian.geolocalisation.application.Partages.PartageVue;
import bf.fasoguardian.geolocalisation.application.Partages.VuePartagee;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Partage temporaire de la position avec un contact d'urgence (US-SEC-001). */
@RestController
@Tag(name = "Géolocalisation")
class ControleurPartages {

    private static final String PARENT = "hasRole('PARENT')";

    private final Partages partages;

    ControleurPartages(Partages partages) {
        this.partages = partages;
    }

    record DemandePartage(@NotNull UUID contactId, @NotNull Integer dureeMinutes) {
    }

    @Operation(summary = "Partage en cours pour l'enfant")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/enfants/{enfantId}/partage")
    ResponseEntity<PartageVue> enCours(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(partages.enCours(id(jeton), enfantId).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun partage n'est en cours.")));
    }

    @Operation(summary = "Partage la position avec un contact d'urgence pour 15 minutes à 12 heures ; le lien part par SMS")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/enfants/{enfantId}/partage")
    ResponseEntity<PartageVue> partager(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandePartage demande) {
        return ResponseEntity.status(HttpStatus.CREATED).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(partages.partager(id(jeton), enfantId, demande.contactId(), demande.dureeMinutes()));
    }

    @Operation(summary = "Révoque le partage en cours : l'accès du contact cesse aussitôt")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @DeleteMapping("/api/v1/enfants/{enfantId}/partage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoquer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        partages.revoquer(id(jeton), enfantId);
    }

    /** Le contact n'a pas de compte : le jeton aléatoire de son lien personnel tient lieu de preuve. */
    @Operation(summary = "Position partagée, pour le contact qui ouvre son lien")
    @GetMapping("/api/v1/public/partages/{jeton}")
    ResponseEntity<VuePartagee> consulter(@PathVariable String jeton) {
        return sansCache(partages.consulter(jeton));
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
