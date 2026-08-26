package com.kenlikdev.qmarket.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Swagger UI: кнопка Authorize → вставить accessToken (без слова Bearer).
 */
@Configuration
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

                        **Auth:** `POST /api/v1/auth/login` → скопировать `accessToken` →
                        кнопка **Authorize** (сверху справа) → вставить токен → Authorize.

                        **Admin seed:** admin@qmarket.local / admin123
                        """.trimIndent(),
                    ).version("0.1.0-WIP")
                    .contact(Contact().name("QMarket")),
            ).addSecurityItem(SecurityRequirement().addList(bearer))
            .components(
                Components()
                    .addSecuritySchemes(
                        bearer,
                        SecurityScheme()
                            .name(bearer)
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT access token from /api/v1/auth/login or /register"),
                    ),
            )
    }
}
