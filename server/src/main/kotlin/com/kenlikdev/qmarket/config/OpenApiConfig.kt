package com.kenlikdev.qmarket.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger UI: Authorize → paste accessToken (without the word Bearer).
 *
 * Disabled in production via springdoc.*.enabled=false (application-prod.yml).
 */
@Configuration
@ConditionalOnProperty(name = ["springdoc.api-docs.enabled"], havingValue = "true", matchIfMissing = true)
class OpenApiConfig {
    @Bean
    fun openAPI(): OpenAPI {
        val bearer = "bearerAuth"
        return OpenAPI()
            .info(
                Info()
                    .title("QMarket API")
                    .description(
                        """
                        WIP modular monolith API.

                        **Auth:** `POST /api/v1/auth/login` → copy `accessToken` →
                        **Authorize** in Swagger UI (raw token, no "Bearer " prefix).
                        """.trimIndent(),
                    ).version("0.1.0-WIP")
                    .contact(Contact().name("KenlikDev").email("dev@qmarket.local")),
            ).addSecurityItem(SecurityRequirement().addList(bearer))
            .components(
                Components().addSecuritySchemes(
                    bearer,
                    SecurityScheme()
                        .name(bearer)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT"),
                ),
            )
    }
}
