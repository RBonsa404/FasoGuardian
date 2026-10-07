package bf.fasoguardian;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Démarre le serveur complet sur PostgreSQL 17 / PostGIS 3.5 et vérifie le socle. */
class SocleIT extends TestIntegration {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void chaqueModuleDisposeDeSonSchema() {
        List<String> schemas = jdbc.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);

        assertThat(schemas).contains("identite", "famille", "dispositifs", "telemetrie", "geolocalisation",
                "alertes", "notifications", "abonnements", "audit", "plateforme");
    }

    @Test
    void laBaseEstUnPostgreSql17AvecPostGis35() {
        assertThat(jdbc.queryForObject("SHOW server_version", String.class)).startsWith("17");
        assertThat(jdbc.queryForObject("SELECT postgis_lib_version()", String.class)).startsWith("3.5");
    }

    @Test
    void uneDistanceGeographiqueEstCalculeeEnMetres() {
        // Place de la Nation → Rond-point des Nations Unies (Ouagadougou), environ 1 km.
        Double metres = jdbc.queryForObject("""
                SELECT ST_Distance(
                    ST_SetSRID(ST_MakePoint(-1.5197, 12.3714), 4326)::geography,
                    ST_SetSRID(ST_MakePoint(-1.5280, 12.3686), 4326)::geography)
                """, Double.class);

        assertThat(metres).isBetween(800.0, 1100.0);
    }

    @Test
    void uneRessourceProtegeeSansJetonRenvoieUnProblemeRfc9457() throws Exception {
        mvc.perform(get("/api/v1/ressource-inexistante"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NON_AUTHENTIFIE"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void laSondeDeSanteEstPublique() throws Exception {
        mvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void leContratOpenApiEstPublie() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("API FasoGuardian"));
    }
}
