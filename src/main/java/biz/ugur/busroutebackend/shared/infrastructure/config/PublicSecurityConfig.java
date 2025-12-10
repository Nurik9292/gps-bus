package biz.ugur.busroutebackend.shared.infrastructure.config;

import biz.ugur.busroutebackend.shared.infrastructure.security.SecurityExceptionHandlers;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.OrServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.PathPatternParserServerWebExchangeMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;


@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@Order(3)
public class PublicSecurityConfig {

    @Bean
    public SecurityWebFilterChain publicSecurityFilterChain(ServerHttpSecurity http) {

        return http
                .securityMatcher(new NegatedServerWebExchangeMatcher(
                        new OrServerWebExchangeMatcher(
                                new PathPatternParserServerWebExchangeMatcher("/api/v1/admin/**"),
                                new PathPatternParserServerWebExchangeMatcher("/api/v1/client/**"),
                                new PathPatternParserServerWebExchangeMatcher("/api/v1/mobile/**")
                        )
                ))

                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(SecurityExceptionHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(SecurityExceptionHandlers.accessDeniedHandler())
                )

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/api/v1/routes/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/stops/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/vehicles/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/vehicles/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/v1/duralga/**").permitAll()

                        .pathMatchers(HttpMethod.GET, "/api/v1/legal/**").permitAll()

                        .pathMatchers(HttpMethod.GET, "/api/v1/trip-planning/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/api/v1/trip-planning/**").permitAll()

                        .pathMatchers("/public/**").permitAll()
                        .pathMatchers("/avatars/**").permitAll()
                        .pathMatchers("/banners/**").permitAll()
                        .pathMatchers("/api/v1/avatars/**").permitAll()
                        .pathMatchers("/api/v1/admin/avatars/**").permitAll()
                        .pathMatchers("/api/v1/banners/**").permitAll()

                        .pathMatchers("/swagger-ui.html").permitAll()
                        .pathMatchers("/swagger-ui/**").permitAll()
                        .pathMatchers("/v3/api-docs/**").permitAll()
                        .pathMatchers("/api-docs/**").permitAll()
                        .pathMatchers("/docs/**").permitAll()
                        .pathMatchers("/webjars/**").permitAll()

                        .pathMatchers("/ws/**").permitAll()
                        .pathMatchers("/api/v1/ws/**").permitAll()

                        .pathMatchers("/actuator/health").permitAll()
                        .pathMatchers("/actuator/info").permitAll()

                        .anyExchange().denyAll()
                )
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOriginPatterns(List.of("*"));

        configuration.setAllowedMethods(List.of(
                "GET",
                "POST",
                "PUT",
                "PATCH",
                "DELETE",
                "OPTIONS"
        ));

        configuration.setAllowedHeaders(List.of("*"));

        configuration.setAllowCredentials(true);

        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }
}
