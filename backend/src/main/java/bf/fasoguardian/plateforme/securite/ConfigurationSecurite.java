package bf.fasoguardian.plateforme.securite;

import java.io.IOException;

import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.Problemes;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import tools.jackson.databind.json.JsonMapper;

/**
 * Socle de sécurité HTTP : sans état, tout refusé par défaut, erreurs au format RFC 9457.
 * Les mécanismes d'authentification (JWT, MFA) sont apportés par le module identite.
 */
@Configuration
@EnableMethodSecurity
class ConfigurationSecurite {

    @Bean
    SecurityFilterChain chaineDeFiltres(HttpSecurity http, JsonMapper json) throws Exception {
        http
                // API sans état authentifiée par en-tête Authorization : pas de jeton CSRF nécessaire ici.
                // Les points utilisant un cookie (rafraîchissement) reçoivent leur propre protection dans identite.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .headers(entetes -> entetes
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                        .referrerPolicy(politique -> politique.policy(ReferrerPolicy.NO_REFERRER)))
                .authorizeHttpRequests(regles -> regles
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(erreurs -> erreurs
                        .authenticationEntryPoint((requete, reponse, erreur) -> ecrire(reponse, json,
                                CodeErreur.NON_AUTHENTIFIE, "Une authentification est requise pour accéder à cette ressource."))
                        .accessDeniedHandler((requete, reponse, erreur) -> ecrire(reponse, json,
                                CodeErreur.ACCES_REFUSE, "Vous n'êtes pas autorisé à accéder à cette ressource.")));
        return http.build();
    }

    private static void ecrire(HttpServletResponse reponse, JsonMapper json, CodeErreur code, String detail)
            throws IOException {
        reponse.setStatus(code.statut().value());
        reponse.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        reponse.setCharacterEncoding("UTF-8");
        json.writeValue(reponse.getOutputStream(), Problemes.de(code, detail));
    }
}
