package bf.fasoguardian.plateforme.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contrat OpenAPI 3 publié par springdoc, source de génération du client Angular. */
@Configuration
class ConfigurationOpenApi {

    static final String SCHEMA_JETON = "jetonAcces";

    @Bean
    OpenAPI contratFasoGuardian() {
        return new OpenAPI()
                .info(new Info()
                        .title("API FasoGuardian")
                        .version("v1")
                        .description("API REST de la plateforme FasoGuardian. Dates ISO 8601 en UTC, "
                                + "coordonnées WGS 84, erreurs RFC 9457 avec code métier stable."))
                .components(new Components().addSecuritySchemes(SCHEMA_JETON, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
    }
}
