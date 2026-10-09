package bf.fasoguardian.dispositifs.web;

import java.util.UUID;

import bf.fasoguardian.dispositifs.application.Appairages;
import bf.fasoguardian.dispositifs.application.Maintenance;
import bf.fasoguardian.dispositifs.application.Maintenance.SuiviParent;
import bf.fasoguardian.dispositifs.application.Appairages.BraceletVue;
import bf.fasoguardian.dispositifs.application.Appairages.Motif;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Bracelet d'un enfant : appairage, configuration, perte, vol et désappairage (US-PAR-013, US-PAR-014). */
@RestController
@RequestMapping("/api/v1/enfants/{enfantId}/bracelet")
@Tag(name = "Bracelet")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasRole('PARENT')")
class ControleurBracelet {

    private final Appairages appairages;

    private final Maintenance maintenance;

    ControleurBracelet(Appairages appairages, Maintenance maintenance) {
        this.appairages = appairages;
        this.maintenance = maintenance;
    }

    record DemandeAppairage(@NotBlank @Size(max = 16) String code) {
    }

    record DemandeDeclaration(@NotNull Motif motif, @Size(max = 6) String codeSecondFacteur) {
    }

    record DemandeConfiguration(@NotNull Boolean modeEconomie) {
    }

    @Operation(summary = "Bracelet actuellement associé à l'enfant")
    @GetMapping
    ResponseEntity<BraceletVue> bracelet(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(appairages.braceletDe(id(jeton), enfantId).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun bracelet n'est associé à cet enfant.")));
    }

    @Operation(summary = "Ticket de maintenance en cours pour le bracelet de l'enfant, ouvert quand il ne répond plus")
    @GetMapping("/maintenance")
    ResponseEntity<SuiviParent> maintenance(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(maintenance.suiviPour(id(jeton), enfantId).orElseThrow(
                () -> new ErreurMetier(CodeErreur.RESSOURCE_INTROUVABLE, "Aucun ticket de maintenance n'est ouvert pour ce bracelet.")));
    }

    @Operation(summary = "Associe un bracelet par le code de sa carte d'activation (distinct du QR gravé)")
    @PostMapping("/appairage")
    ResponseEntity<BraceletVue> associer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeAppairage demande) {
        return sansCache(appairages.associer(id(jeton), enfantId, demande.code()));
    }

    @Operation(summary = "Déclare le bracelet perdu, volé ou cassé (second facteur DECLARER_BRACELET requis)")
    @PostMapping("/declaration")
    ResponseEntity<BraceletVue> declarer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeDeclaration demande) {
        return sansCache(appairages.declarer(id(jeton), enfantId, demande.motif(), demande.codeSecondFacteur()));
    }

    @Operation(summary = "Le bracelet perdu a été retrouvé pendant les 72 h de suivi")
    @PostMapping("/retrouve")
    ResponseEntity<BraceletVue> retrouver(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache(appairages.retrouver(id(jeton), enfantId));
    }

    @Operation(summary = "Active ou désactive le mode économie ; la modification est notifiée aux tuteurs")
    @PutMapping("/configuration")
    ResponseEntity<BraceletVue> configurer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeConfiguration demande) {
        return sansCache(appairages.reglerModeEconomie(id(jeton), enfantId, demande.modeEconomie()));
    }

    @Operation(summary = "Localiser maintenant : demande une position immédiate au bracelet (une fois par minute)")
    @PostMapping("/localisation")
    ResponseEntity<Void> localiser(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        appairages.localiserMaintenant(id(jeton), enfantId);
        return ResponseEntity.accepted().build();
    }

    @Operation(summary = "Désappaire le bracelet sans déclaration")
    @DeleteMapping
    ResponseEntity<Void> desappairer(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        appairages.desappairer(id(jeton), enfantId);
        return ResponseEntity.noContent().build();
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static <T> ResponseEntity<T> sansCache(T corps) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(corps);
    }
}
