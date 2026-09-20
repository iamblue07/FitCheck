package com.sewlect.common.openapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI sewlectOpenAPI() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT");

        Info info = new Info()
                .title("Sewlect API")
                .version("v1")
                .description("""
                        Backend API for Sewlect: AI-driven outfit recommendations and virtual try-on.

                        Authentication is a Bearer access token obtained from POST /api/v1/auth/login or
                        POST /api/v1/auth/register. Access tokens are short-lived; POST /api/v1/auth/refresh
                        exchanges a refresh token for a new pair and rotates the refresh token.

                        Errors share one shape: timestamp, status, error, message, path, correlationId and,
                        for validation failures, a fieldErrors map keyed by field name. The correlationId also
                        comes back on every response as the X-Correlation-Id header.
                        """);

        return new OpenAPI()
                .info(info)
                .components(new Components().addSecuritySchemes(BEARER_SCHEME_NAME, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME_NAME));
    }
}