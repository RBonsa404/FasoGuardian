package bf.fasoguardian.plateforme.planification;

import javax.sql.DataSource;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Traitements planifiés verrouillés par ShedLock : un traitement ne s'exécute qu'une fois
 * lorsque plusieurs instances du serveur sont actives (FG-DOC-06, section 6.5).
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
class ConfigurationPlanification {

    @Bean
    LockProvider fournisseurDeVerrous(DataSource source) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(source))
                .withTableName("plateforme.shedlock")
                .usingDbTime()
                .build());
    }
}
