package biz.ugur.busroutebackend.admin.infrastructure.security;

import biz.ugur.busroutebackend.shared.infrastructure.security.SecurityExceptionHandlers;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;

@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Order(1)
public class AdminSecurityConfig {

    @Bean
    public SecurityWebFilterChain adminSecurityFilterChain(
            ServerHttpSecurity http,
            AdminJwtTokenService adminJwtTokenService,
            TokenBlacklistService tokenBlacklistService) {

        AdminAuthenticationFilter adminAuthenticationFilter =
                new AdminAuthenticationFilter(adminJwtTokenService, tokenBlacklistService);

        return http
                .securityMatcher(new PathPatternParserServerWebExchangeMatcher("/api/v1/admin/**"))

                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)
                .anonymous(ServerHttpSecurity.AnonymousSpec::disable)

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(SecurityExceptionHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(SecurityExceptionHandlers.accessDeniedHandler())
                )

                .addFilterAt(adminAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/v1/admin/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/admin/auth/refresh").permitAll()

                        .pathMatchers(HttpMethod.GET, "/api/v1/admin/auth/me").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/admin/auth/profile").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/admin/auth/logout").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/v1/admin/auth/change-password").hasRole("ADMIN")

                        .pathMatchers("/api/v1/admin/routes", "/api/v1/admin/routes/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/admin/stops", "/api/v1/admin/stops/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/admin/banners", "/api/v1/admin/banners/**").hasRole("ADMIN")
                        .pathMatchers("/api/v1/admin/cities", "/api/v1/admin/cities/**").hasRole("ADMIN")

                        .pathMatchers("/api/v1/admin/users", "/api/v1/admin/users/**").hasRole("SUPER_ADMIN")
                        .pathMatchers("/api/v1/admin/system", "/api/v1/admin/system/**").hasRole("SUPER_ADMIN")
                        .pathMatchers(HttpMethod.GET, "/api/v1/admin/logs", "/api/v1/admin/logs/**").hasRole("SUPER_ADMIN")

                        .anyExchange().hasRole("ADMIN")
                )
                .build();
    }
}


