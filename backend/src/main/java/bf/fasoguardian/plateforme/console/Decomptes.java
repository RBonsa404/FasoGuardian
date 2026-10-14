package bf.fasoguardian.plateforme.console;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Aide aux indicateurs : décomptes SQL et bornes de temps, dans le fuseau du service. */
@Component
public class Decomptes {

    private final JdbcTemplate jdbc;
    private final Clock horloge;
    private final ZoneId fuseau;

    Decomptes(JdbcTemplate jdbc, Clock horloge, @Value("${fasoguardian.fuseau:Africa/Ouagadougou}") ZoneId fuseau) {
        this.jdbc = jdbc;
        this.horloge = horloge;
        this.fuseau = fuseau;
    }

    public long compter(String sql, Object... parametres) {
        Long n = jdbc.queryForObject(sql, Long.class, parametres);
        return n == null ? 0 : n;
    }

    /** Valeur décimale, ou {@code null} quand la requête n'a rien à mesurer. */
    public Double mesurer(String sql, Object... parametres) {
        return jdbc.queryForObject(sql, Double.class, parametres);
    }

    public Timestamp ilYA(Duration duree) {
        return Timestamp.from(horloge.instant().minus(duree));
    }

    public Timestamp debutDuJour() {
        return Timestamp.from(LocalDate.now(horloge.withZone(fuseau)).atStartOfDay(fuseau).toInstant());
    }
}
