package bf.fasoguardian.identite.web;

import java.time.Duration;
import java.util.UUID;

import bf.fasoguardian.identite.SecondFacteur.ActionSensible;
import bf.fasoguardian.identite.application.ProfilParent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Profil du parent : récupération d'accès, coordonnées, second facteur et clôture (US-PAR-002). */
@RestController
@Tag(name = "Profil")
class ControleurProfil {

    private static final String PARENT = "hasRole('PARENT')";

    private final ProfilParent profil;

    ControleurProfil(ProfilParent profil) {
        this.profil = profil;
    }

    record DemandeCodeReinitialisation(@NotBlank @Size(max = 32) String telephone) {
    }

    record DemandeReinitialisation(@NotBlank @Size(max = 32) String telephone, @NotBlank @Size(max = 12) String code,
            @NotBlank @Size(max = 256) String motDePasse) {
    }

    record DemandeChangementMotDePasse(@NotBlank @Size(max = 256) String actuel, @NotBlank @Size(max = 256) String nouveau) {
    }

    record DemandeCodeTelephone(@NotBlank @Size(max = 32) String telephone) {
    }

    record DemandeChangementTelephone(@NotBlank @Size(max = 32) String telephone, @NotBlank @Size(max = 12) String code,
            @NotBlank @Size(max = 256) String motDePasse) {
    }

    record DemandeSecondFacteur(@NotNull ActionSensible action) {
    }

    record DemandeCloture(@Size(max = 12) String codeSecondFacteur) {
    }

    @Operation(summary = "Mot de passe oublié, étape 1 : envoie un code au numéro s'il correspond à un compte")
    @PostMapping("/api/v1/auth/mot-de-passe/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void codeReinitialisation(@Valid @RequestBody DemandeCodeReinitialisation demande) {
        profil.demanderReinitialisation(demande.telephone());
    }

    @Operation(summary = "Mot de passe oublié, étape 2 : nouveau mot de passe contre le code reçu ; ferme toutes les sessions")
    @PostMapping("/api/v1/auth/mot-de-passe/reinitialiser")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reinitialiser(@Valid @RequestBody DemandeReinitialisation demande) {
        profil.reinitialiser(demande.telephone(), demande.code(), demande.motDePasse());
    }

    @Operation(summary = "Change le mot de passe (mot de passe actuel requis)")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/moi/mot-de-passe")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changerMotDePasse(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeChangementMotDePasse demande) {
        profil.changerMotDePasse(id(jeton), demande.actuel(), demande.nouveau());
    }

    @Operation(summary = "Changement de numéro, étape 1 : envoie un code au nouveau numéro")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/moi/telephone/code")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void codeTelephone(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeCodeTelephone demande) {
        profil.demanderChangementTelephone(id(jeton), demande.telephone());
    }

    @Operation(summary = "Changement de numéro, étape 2 : code reçu sur le nouveau numéro et mot de passe actuel")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/moi/telephone")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void changerTelephone(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeChangementTelephone demande) {
        profil.changerTelephone(id(jeton), demande.telephone(), demande.code(), demande.motDePasse());
    }

    @Operation(summary = "Envoie par SMS le code de confirmation d'une action sensible")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/moi/second-facteur")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void secondFacteur(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeSecondFacteur demande) {
        profil.envoyerCode(id(jeton), demande.action());
    }

    @Operation(summary = "Clôt le compte (second facteur CLORE_COMPTE requis) ; données supprimées sous 30 jours")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/moi/cloture")
    ResponseEntity<Void> clore(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeCloture demande) {
        profil.clore(id(jeton), demande.codeSecondFacteur());
        ResponseCookie expire = ResponseCookie.from(ControleurAuthentification.COOKIE, "").httpOnly(true).secure(true)
                .sameSite("Strict").path(ControleurAuthentification.CHEMIN_COOKIE).maxAge(Duration.ZERO).build();
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, expire.toString()).build();
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }
}
