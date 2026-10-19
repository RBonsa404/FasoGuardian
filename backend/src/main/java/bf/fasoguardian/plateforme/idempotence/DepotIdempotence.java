package bf.fasoguardian.plateforme.idempotence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Clés d'idempotence des écritures rejouées, gardées 24 heures (V33). */
@Component
public class DepotIdempotence {

    /** Un client hors ligne plus d'une journée rejoue ses actions ; au-delà, la clé n'est plus reconnue. */
    static final Duration CONSERVATION = Duration.ofHours(24);

    record Requete(String empreinte, Integer statut) {
    }

    private final JdbcTemplate jdbc;
    private final Clock horloge;

    DepotIdempotence(JdbcTemplate jdbc, Clock horloge) {
        this.jdbc = jdbc;
        this.horloge = horloge;
    }

    /** @return vrai si la clé était libre : la requête est alors la première à s'exécuter */
    boolean reserver(String sujet, String cle, String empreinte, Instant maintenant) {
        return jdbc.update("""
                INSERT INTO plateforme.requete_idempotente (sujet, cle, empreinte, statut, creee_le)
                VALUES (?, ?, ?, NULL, ?) ON CONFLICT (sujet, cle) DO NOTHING
                """, sujet, cle, empreinte, Timestamp.from(maintenant)) == 1;
    }

    Optional<Requete> lire(String sujet, String cle) {
        return jdbc.query("SELECT empreinte, statut FROM plateforme.requete_idempotente WHERE sujet = ? AND cle = ?",
                (ligne, rang) -> new Requete(ligne.getString("empreinte"), (Integer) ligne.getObject("statut")), sujet, cle)
                .stream().findFirst();
    }

    void terminer(String sujet, String cle, int statut) {
        jdbc.update("UPDATE plateforme.requete_idempotente SET statut = ? WHERE sujet = ? AND cle = ?", statut, sujet, cle);
    }

    void oublier(String sujet, String cle) {
        jdbc.update("DELETE FROM plateforme.requete_idempotente WHERE sujet = ? AND cle = ?", sujet, cle);
    }

    @Scheduled(cron = "${fasoguardian.idempotence.purge:0 17 * * * *}")
    @SchedulerLock(name = "plateforme-purge-idempotence", lockAtMostFor = "PT5M")
    public void purger() {
        jdbc.update("DELETE FROM plateforme.requete_idempotente WHERE creee_le < ?",
                Timestamp.from(horloge.instant().minus(CONSERVATION)));
    }
}
