package bf.fasoguardian.identite.web;

import java.util.List;
import java.util.UUID;

import bf.fasoguardian.identite.application.InvitationsTuteur;
import bf.fasoguardian.identite.application.InvitationsTuteur.InvitationRecue;
import bf.fasoguardian.identite.application.InvitationsTuteur.InvitationVue;
import bf.fasoguardian.identite.application.InvitationsTuteur.TuteursEnfant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

/** Tuteurs d'un enfant et invitation d'un second tuteur (US-PAR-003, écran 34). */
@RestController
@Tag(name = "Tuteurs")
@SecurityRequirement(name = "jetonAcces")
class ControleurTuteurs {

    private static final String PARENT = "hasRole('PARENT')";

    private final InvitationsTuteur invitations;

    ControleurTuteurs(InvitationsTuteur invitations) {
        this.invitations = invitations;
    }

    record DemandeInvitation(@NotBlank @Size(max = 32) String telephone, @Size(max = 6) String codeSecondFacteur) {
    }

    @Operation(summary = "Tuteurs de l'enfant et invitation en cours")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/enfants/{enfantId}/tuteurs")
    ResponseEntity<TuteursEnfant> tuteurs(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        return sansCache().body(invitations.tuteurs(id(jeton), enfantId));
    }

    @Operation(summary = "Invite un second tuteur ; confirmation par code SMS (action AJOUTER_TUTEUR)")
    @PreAuthorize(PARENT)
    @PostMapping("/api/v1/enfants/{enfantId}/tuteurs/invitation")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationVue inviter(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId,
            @Valid @RequestBody DemandeInvitation demande) {
        return invitations.inviter(id(jeton), enfantId, demande.telephone(), demande.codeSecondFacteur());
    }

    @Operation(summary = "Annule l'invitation en cours, tant que l'invité n'est pas vérifié")
    @PreAuthorize(PARENT)
    @DeleteMapping("/api/v1/enfants/{enfantId}/tuteurs/invitation")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void annuler(@AuthenticationPrincipal Jwt jeton, @PathVariable UUID enfantId) {
        invitations.annuler(id(jeton), enfantId);
    }

    @Operation(summary = "Invitations reçues sur le numéro du compte, encore valables")
    @PreAuthorize(PARENT)
    @GetMapping("/api/v1/moi/invitations")
    ResponseEntity<List<InvitationRecue>> recues(@AuthenticationPrincipal Jwt jeton) {
        return sansCache().body(invitations.recues(id(jeton)));
    }

    private static UUID id(Jwt jeton) {
        return UUID.fromString(jeton.getSubject());
    }

    private static ResponseEntity.BodyBuilder sansCache() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store");
    }
}
