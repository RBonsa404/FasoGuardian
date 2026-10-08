package bf.fasoguardian.plateforme.securite;

import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Publie un {@link AccesRefuse} pour chaque refus opposé à un utilisateur authentifié (REQ-SYS-016). */
@Component
public class JournalisationRefus {

    private final ApplicationEventPublisher evenements;

    JournalisationRefus(ApplicationEventPublisher evenements) {
        this.evenements = evenements;
    }

    public void publier(HttpServletRequest requete) {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jeton) {
            String roles = jeton.getAuthorities().stream()
                    .map(autorite -> autorite.getAuthority())
                    .filter(autorite -> autorite.startsWith("ROLE_"))
                    .map(autorite -> autorite.substring(5))
                    .sorted().collect(Collectors.joining(","));
            evenements.publishEvent(new AccesRefuse(UUID.fromString(jeton.getToken().getSubject()), roles,
                    requete.getMethod(), requete.getRequestURI()));
        }
    }
}
