package com.kenlikdev.qmarket.common.config

import com.kenlikdev.qmarket.common.exception.ErrorResponse
import com.kenlikdev.qmarket.common.security.JwtAuthenticationFilter
import com.kenlikdev.qmarket.common.security.JwtProperties
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import tools.jackson.databind.ObjectMapper

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties::class)
open class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
    @Value("\${qmarket.security.cors.allowed-origin-patterns:http://localhost:*,http://127.0.0.1:*,http://10.0.2.2:*}")
    private val corsOriginPatterns: String,
    /**
     * When false (prod), Swagger / OpenAPI paths are not anonymous.
     * Local/dev keep true so Swagger UI works without JWT.
     */
    @Value("\${qmarket.security.api-docs-public:true}")
    private val apiDocsPublic: Boolean,
) {
    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        corsConfigurationSource: CorsConfigurationSource,
    ): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/v1/auth/**",
                        "/api/v1/payments/stripe/webhook",
                        "/actuator/health",
                        "/actuator/info",
                    ).permitAll()
                if (apiDocsPublic) {
                    auth
                        .requestMatchers(
                            "/swagger-ui/**",
                            "/swagger-ui.html",
                            "/api-docs/**",
                            "/v3/api-docs/**",
                        ).permitAll()
                }
                auth
                    .requestMatchers("/actuator/**")
                    .hasAnyRole("ADMIN", "MANAGER")
                    .requestMatchers(
                        HttpMethod.GET,
                        "/api/v1/products/admin/**",
                        "/api/v1/categories/admin/**",
                    )
                    .hasAnyRole("ADMIN", "MANAGER")
                    .requestMatchers(HttpMethod.GET, "/api/v1/products/**", "/api/v1/categories/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated()
            }
            .exceptionHandling {
                it.authenticationEntryPoint { request, response, _ ->
                    writeSecurityError(
                        response = response,
                        status = HttpStatus.UNAUTHORIZED,
                        code = "UNAUTHORIZED",
                        message = "Authentication is required",
                        path = request.requestURI,
                    )
                }
                it.accessDeniedHandler { request, response, _ ->
                    writeSecurityError(
                        response = response,
                        status = HttpStatus.FORBIDDEN,
                        code = "FORBIDDEN",
                        message = "Access denied",
                        path = request.requestURI,
                    )
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()

    @Bean
    fun authenticationManager(config: AuthenticationConfiguration): AuthenticationManager = config.authenticationManager

    private fun writeSecurityError(
        response: HttpServletResponse,
        status: HttpStatus,
        code: String,
        message: String,
        path: String,
    ) {
        response.status = status.value()
        response.contentType = "application/json"
        response.characterEncoding = Charsets.UTF_8.name()
        objectMapper.writeValue(
            response.writer,
            ErrorResponse(
                status = status.value(),
                error = status.reasonPhrase,
                code = code,
                message = message,
                path = path,
            ),
        )
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val patterns =
            corsOriginPatterns
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val config =
            CorsConfiguration().apply {
                allowedOriginPatterns = patterns
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                allowCredentials = true
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }
    }
}
