package com.renault.fleet.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html. Every operation needs a bearer token unless it opts out (the auth endpoints do). */
@Configuration
public class OpenApiConfig {

    static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI fleetOpenApi(@Value("${fleet.version:dev}") String version) {
        return new OpenAPI()
                .info(new Info()
                        .title("Renault Group Fleet Portal API")
                        .version(version)
                        .description("HQ-side B2B fleet management. Errors are RFC 7807 ProblemDetail."))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token from POST /api/auth/login (valid 15 minutes).")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
