package bf.fasoguardian.identite.web;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import bf.fasoguardian.identite.application.AgentsInternes;
import bf.fasoguardian.identite.application.AgentsInternes.Agent;
import bf.fasoguardian.identite.application.Sessions.Session;
import bf.fasoguardian.identite.domaine.RoleInterne;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Connexion des agents internes et gestion de leurs comptes par l'administrateur (US-ADM-001). */
@RestController
@Tag(name = "Agents internes")
class ControleurAgents {

    private final AgentsInternes agents;

    ControleurAgents(AgentsInternes agents) {
        this.agents = agents;
    }

    record DemandeConnexionAgent(@NotBlank @Size(max = 64) String identifiant,
            @NotBlank @Size(max = 256) String motDePasse, @Size(max = 12) String codeTotp) {
    }

    record DemandeCreationAgent(@NotBlank @Size(max = 64) String identifiant,
            @NotBlank @Size(max = 256) String motDePasseProvisoire, @NotEmpty Set<RoleInterne> roles) {
    }

    record AgentDto(String id, String identifiant, Set<RoleInterne> roles, boolean secondFacteurActif) {
    }

    @Operation(summary = "Connexion d'un agent : identifiant, mot de passe et code TOTP",
            description = "Sans second facteur actif, répond 403 TOTP_A_ACTIVER avec le secret à enregistrer ; "
                    + "le premier code valide active le second facteur.")
    @PostMapping("/api/v1/auth/agents/connexion")
    ResponseEntity<ControleurAuthentification.JetonAccesDto> connexion(@Valid @RequestBody DemandeConnexionAgent demande) {
        Session session = agents.connecter(demande.identifiant(), demande.motDePasse(), demande.codeTotp());
        return ControleurAuthentification.reponse(HttpStatus.OK, session);
    }

    @Operation(summary = "Crée un agent et lui attribue ses rôles")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/api/v1/admin/agents")
    @ResponseStatus(HttpStatus.CREATED)
    AgentDto creer(@AuthenticationPrincipal Jwt jeton, @Valid @RequestBody DemandeCreationAgent demande) {
        return dto(agents.creer(UUID.fromString(jeton.getSubject()), demande.identifiant(),
                demande.motDePasseProvisoire(), demande.roles()));
    }

    @Operation(summary = "Liste les agents et leurs rôles")
    @SecurityRequirement(name = "jetonAcces")
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/api/v1/admin/agents")
    ResponseEntity<List<AgentDto>> lister() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(agents.lister().stream().map(ControleurAgents::dto).toList());
    }

    private static AgentDto dto(Agent agent) {
        return new AgentDto(agent.id().toString(), agent.identifiant(), agent.roles(), agent.secondFacteurActif());
    }
}
