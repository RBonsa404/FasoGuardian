package bf.fasoguardian.identite.web;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import bf.fasoguardian.identite.application.Litiges;
import bf.fasoguardian.identite.application.Litiges.Mesures;
import bf.fasoguardian.plateforme.erreurs.CodeErreur;
import bf.fasoguardian.plateforme.erreurs.ErreurMetier;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Mesures conservatoires d'un litige de filiation (US-KYC-001), appliquées en un seul endroit à toutes les
 * routes d'un tuteur : pendant l'instruction, il ne peut plus rien modifier de ce qui concerne l'enfant, ni
 * changer de numéro ou clore son compte ; si la géolocalisation est suspendue, il ne voit plus la position.
 * Ce qui protège l'enfant reste ouvert : prise en charge d'une alerte, signalement, paiement de l'abonnement.
 */
@Configuration
class GelPendantLitige implements WebMvcConfigurer, HandlerInterceptor {

    private static final Pattern ENFANT = Pattern.compile("^/api/v1/enfants/([0-9a-fA-F-]{36})(/.*)?$");
    private static final Set<String> LECTURES = Set.of("GET", "HEAD", "OPTIONS");
    /** Actions de sécurité et paiement : jamais gelés. */
    private static final Pattern TOUJOURS_PERMIS = Pattern.compile("^/(alertes/prise-en-charge|signalement|abonnement/paiements)$");
    /** Ce qui montre ou transmet la position. */
    private static final Pattern POSITION = Pattern.compile("^/(position|trajets|partage|bracelet/localisation)$");
    /** Réglages du compte gelés pendant un litige ; le mot de passe reste modifiable, par sécurité. */
    private static final Set<String> COMPTE_GELE = Set.of("/api/v1/moi/telephone", "/api/v1/moi/telephone/code", "/api/v1/moi/cloture");

    private final Litiges litiges;

    GelPendantLitige(Litiges litiges) {
        this.litiges = litiges;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registre) {
        registre.addInterceptor(this).addPathPatterns("/api/v1/enfants/**", "/api/v1/moi/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object gestionnaire) {
        UUID tuteurId = tuteur();
        if (tuteurId == null) {
            return true;
        }
        String chemin = requete.getRequestURI();
        boolean lecture = LECTURES.contains(requete.getMethod());
        if (COMPTE_GELE.contains(chemin)) {
            if (!lecture && litiges.enLitige(tuteurId)) {
                throw gele();
            }
            return true;
        }
        Matcher enfant = ENFANT.matcher(chemin);
        if (!enfant.matches()) {
            return true;
        }
        Mesures mesures = litiges.mesures(tuteurId, UUID.fromString(enfant.group(1)));
        String suite = enfant.group(2) == null ? "" : enfant.group(2);
        if (mesures.geolocalisationSuspendue() && POSITION.matcher(suite).matches()) {
            throw new ErreurMetier(CodeErreur.GEOLOCALISATION_SUSPENDUE,
                    "La position est suspendue pendant l'instruction d'un signalement. Le SOS et la page QR restent actifs.");
        }
        if (mesures.compteGele() && !lecture && !TOUJOURS_PERMIS.matcher(suite).matches()) {
            throw gele();
        }
        return true;
    }

    private static ErreurMetier gele() {
        return new ErreurMetier(CodeErreur.COMPTE_GELE,
                "Les réglages sont gelés pendant l'instruction d'un signalement. Vous serez prévenu de la décision.");
    }

    /** Identifiant du parent connecté ; {@code null} pour un agent ou une requête sans session. */
    private static UUID tuteur() {
        Authentication authentification = SecurityContextHolder.getContext().getAuthentication();
        if (authentification == null || !(authentification.getPrincipal() instanceof Jwt jeton)
                || authentification.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_PARENT"))) {
            return null;
        }
        try {
            return UUID.fromString(jeton.getSubject());
        } catch (IllegalArgumentException erreur) {
            return null;
        }
    }
}
