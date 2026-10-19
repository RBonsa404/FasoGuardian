package bf.fasoguardian.plateforme.mesure;

import java.io.IOException;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Temps de réponse de l'API, toutes routes confondues (FG-DOC-04 : moins de 500 ms au 95e centile). Les métriques
 * de Spring sont tenues route par route, et des 95e centiles par route ne se combinent pas : ce chronomètre unique
 * donne l'indicateur que la supervision suit et que l'écran 72 affiche.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MesureDesRequetes extends OncePerRequestFilter {

    public static final String METRIQUE = "fasoguardian.api.duree";

    private final Timer duree;

    MesureDesRequetes(MeterRegistry metriques) {
        this.duree = Timer.builder(METRIQUE).description("Temps de réponse de l'API, toutes routes confondues")
                // Le centile sert l'écran 72 ; l'histogramme, la règle Prometheus qui agrège les instances.
                .publishPercentiles(0.95).publishPercentileHistogram().register(metriques);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest requete) {
        return !requete.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest requete, HttpServletResponse reponse, FilterChain suite)
            throws ServletException, IOException {
        Timer.Sample debut = Timer.start();
        try {
            suite.doFilter(requete, reponse);
        } finally {
            debut.stop(duree);
        }
    }
}
