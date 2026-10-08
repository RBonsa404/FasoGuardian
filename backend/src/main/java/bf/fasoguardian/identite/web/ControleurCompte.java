package bf.fasoguardian.identite.web;

import java.util.Set;

import bf.fasoguardian.identite.application.ConsultationCompte;
import bf.fasoguardian.identite.application.ConsultationCompte.Compte;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/moi")
@Tag(name = "Compte")
@SecurityRequirement(name = "jetonAcces")
class ControleurCompte {

    private final ConsultationCompte comptes;

    ControleurCompte(ConsultationCompte comptes) {
        this.comptes = comptes;
    }

    record CompteDto(String id, String statut, String telephoneMasque, Set<String> roles) {
    }

    @Operation(summary = "Compte de l'utilisateur authentifié")
    @GetMapping
    CompteDto moi(@AuthenticationPrincipal Jwt jeton) {
        Compte compte = comptes.de(java.util.UUID.fromString(jeton.getSubject()));
        return new CompteDto(compte.id().toString(), compte.statut().name(), compte.telephoneMasque(), compte.roles());
    }
}
