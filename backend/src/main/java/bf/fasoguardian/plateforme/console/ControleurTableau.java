package bf.fasoguardian.plateforme.console;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import bf.fasoguardian.plateforme.console.IndicateursConsole.Indicateur;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tableau de bord de la console (écran 57) : les indicateurs des seules files des rôles de l'agent. */
@RestController
@RequestMapping("/api/v1/console/tableau")
@Tag(name = "Console")
@SecurityRequirement(name = "jetonAcces")
@PreAuthorize("hasAnyRole('KYC', 'SUPPORT', 'SAV', 'FDS', 'ADMIN')")
class ControleurTableau {

    private static final List<String> ORDRE = List.of("KYC", "SUPPORT", "SAV", "FDS", "ADMIN");

    record GroupeIndicateurs(String role, List<Indicateur> indicateurs) {
    }

    private final List<IndicateursConsole> sources;

    ControleurTableau(List<IndicateursConsole> sources) {
        this.sources = sources;
    }

    @Operation(summary = "Indicateurs des files de travail de chacun des rôles de l'agent")
    @GetMapping
    ResponseEntity<List<GroupeIndicateurs>> tableau(Authentication authentification) {
        Set<String> roles = authentification.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toSet());
        UUID agent = UUID.fromString(authentification.getName());
        List<GroupeIndicateurs> groupes = sources.stream().filter(source -> roles.contains(source.role()))
                .sorted(Comparator.comparingInt(source -> ORDRE.indexOf(source.role())))
                .map(source -> new GroupeIndicateurs(source.role(), source.pour(agent))).toList();
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(groupes);
    }
}
