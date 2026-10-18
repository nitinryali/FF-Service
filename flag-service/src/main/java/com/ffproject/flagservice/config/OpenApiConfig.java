package com.ffproject.flagservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata for the Flag Service.
 *
 * <p>Registers the {@code bearerAuth} HTTP bearer scheme so that Swagger UI can
 * inject a JWT into the {@code Authorization} header, and applies it as a global
 * security requirement. The generated document is served at
 * {@code /v3/api-docs} with the UI at {@code /swagger-ui.html}
 * (Requirement 7.1).</p>
 */
@Configuration
public class OpenApiConfig {

    /** Name of the security scheme referenced by {@code @SecurityRequirement}. */
    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    /**
     * Builds the service's OpenAPI document.
     *
     * @return the configured OpenAPI model
     */
    @Bean
    public OpenAPI flagServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Flag Service API")
                        .version("1.0.0")
                        .description("Feature flag management REST API"))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, bearerAuthScheme()))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
    }

    /**
     * Describes how clients authenticate: a JWT in the {@code Authorization}
     * header, prefixed with {@code Bearer }.
     *
     * @return the bearer-JWT security scheme
     */
    private SecurityScheme bearerAuthScheme() {
        return new SecurityScheme()
                .name(SECURITY_SCHEME_NAME)
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");
    }
}
