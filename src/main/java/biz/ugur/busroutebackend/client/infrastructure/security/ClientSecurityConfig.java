package biz.ugur.busroutebackend.client.infrastructure.security;

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
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;


@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Order(2)
public class ClientSecurityConfig {

    @Bean
    public SecurityWebFilterChain clientSecurityFilterChain(
            ServerHttpSecurity http,
            ClientJwtTokenService clientJwtTokenService,
            TokenBlacklistService tokenBlacklistService) {
        log.info("Configuring Client Security Filter Chain for /api/v1/client/** and /api/v1/mobile/**");

        ClientAuthenticationFilter clientAuthenticationFilter =
                new ClientAuthenticationFilter(clientJwtTokenService, tokenBlacklistService);

        return http
                .securityMatcher(new OrServerWebExchangeMatcher(
                        new PathPatternParserServerWebExchangeMatcher("/api/v1/client/**"),
                        new PathPatternParserServerWebExchangeMatcher("/api/v1/mobile/**")
                ))

                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(SecurityExceptionHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(SecurityExceptionHandlers.accessDeniedHandler())
                )

                .addFilterAt(clientAuthenticationFilter, SecurityWebFiltersOrder.AUTHENTICATION)
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/register").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/center-auth").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/refresh").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/verify").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/resend-verification").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/forgot-password").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/reset-password").permitAll()

                        .pathMatchers(HttpMethod.GET, "/api/v1/client/auth/me").authenticated()
                        .pathMatchers(HttpMethod.PATCH, "/api/v1/client/auth/profile").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/logout").authenticated()
                        .pathMatchers(HttpMethod.POST, "/api/v1/client/auth/change-password").authenticated()

                        .pathMatchers("/api/v1/client/favorites/**").authenticated()

//                        .pathMatchers(HttpMethod.GET, "/api/v1/mobile/routes/**").permitAll()
//                        .pathMatchers(HttpMethod.GET, "/api/v1/mobile/stops/**").permitAll()
//                        .pathMatchers(HttpMethod.GET, "/api/v1/mobile/vehicles/**").permitAll()
//                        .pathMatchers(HttpMethod.GET, "/api/v1/mobile/banners/**").permitAll()

                        .anyExchange().authenticated()
                )
                .build();
    }
}
