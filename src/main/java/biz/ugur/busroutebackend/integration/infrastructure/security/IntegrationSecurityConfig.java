package biz.ugur.busroutebackend.integration.infrastructure.security;

import biz.ugur.busroutebackend.integration.domain.repository.ExternalServiceRepository;
import biz.ugur.busroutebackend.shared.infrastructure.security.SecurityExceptionHandlers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

/**
 * Security configuration for external API token authentication
 * This filter chain runs BEFORE client JWT authentication (Order=1 vs Order=2)
 * to intercept requests with API tokens
 */
@Slf4j
@Configuration
@EnableWebFluxSecurity
@Order(1)
public class IntegrationSecurityConfig {

    @Bean
    public SecurityWebFilterChain integrationSecurityFilterChain(
            ServerHttpSecurity http,
            ExternalServiceRepository externalServiceRepository,
            ApiTokenRateLimiter rateLimiter) {

        log.info("Configuring Integration Security Filter Chain for external API token authentication");

        ApiTokenAuthenticationFilter apiTokenFilter =
                new ApiTokenAuthenticationFilter(externalServiceRepository, rateLimiter);

        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/mobile/**"))

                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(SecurityExceptionHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(SecurityExceptionHandlers.accessDeniedHandler())
                )

                .addFilterAt(apiTokenFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                // Mobile endpoints can be accessed either by:
                // 1. External services with API tokens (authenticated by this filter chain)
                // 2. Mobile app with client JWT (authenticated by ClientSecurityConfig)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/api/v1/mobile/**").permitAll()
                        .anyExchange().authenticated()
                )
                .build();
    }
}
