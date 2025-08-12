package biz.ugur.busroutebackend.shared.infrastructure.config;

import biz.ugur.busroutebackend.shared.infrastructure.security.JwtAuthenticationFilter;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtService;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;


@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
public class SecurityConfig {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    public SecurityConfig(JwtService jwtService, TokenBlacklistService tokenBlacklistService) {
        this.jwtService = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(customAuthenticationEntryPoint())
                        .accessDeniedHandler(customAccessDeniedHandler())
                )

                .addFilterAfter(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.POST, "/admin/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/admin/auth/refresh").permitAll()

                        .pathMatchers(HttpMethod.GET, "/public/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/routes/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/stops/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/cities/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/trips/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/vehicles/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/trip-planning/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/trip-planning/**").permitAll()
                        .pathMatchers("/avatars/**").permitAll()
                        .pathMatchers("/banners/**").permitAll()
                        .pathMatchers("/api/v1/avatars/**").permitAll()
                        .pathMatchers("/api/v1/banners/**").permitAll()

                        .pathMatchers("/ws/**").permitAll()


//                        .pathMatchers(HttpMethod.GET, "/admin/auth/me").hasRole("ADMIN")
//                        .pathMatchers(HttpMethod.PATCH, "/admin/auth/profile").hasRole("ADMIN")
//                        .pathMatchers(HttpMethod.POST, "/admin/auth/logout").hasRole("ADMIN")
//                        .pathMatchers(HttpMethod.POST, "/admin/auth/change-password").hasRole("ADMIN")
//
//                        .pathMatchers("/admin/routes/**").hasRole("ADMIN")
//                        .pathMatchers("/admin/stops/**").hasRole("ADMIN")
//                        .pathMatchers("/admin/buses/**").hasRole("ADMIN")
//                        .pathMatchers("/admin/banners/**").hasRole("ADMIN")
//                        .pathMatchers("/admin/cities/**").hasRole("ADMIN")
//
//                        .pathMatchers("/admin/users/**").hasRole("SUPER_ADMIN")
//                        .pathMatchers("/admin/system/**").hasRole("SUPER_ADMIN")
//                        .pathMatchers(HttpMethod.GET, "/admin/logs/**").hasRole("SUPER_ADMIN")

                        .anyExchange().permitAll()
                )
                .build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService, tokenBlacklistService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public ServerAuthenticationEntryPoint customAuthenticationEntryPoint() {
        return (exchange, ex) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            response.getHeaders().add("Content-Type", "application/json");

            String body = """
                {
                    "error": "unauthorized",
                    "message": "Authentication required",
                    "timestamp": "%s",
                    "path": "%s"
                }
                """.formatted(
                    Instant.now(),
                    exchange.getRequest().getPath().value()
            );

            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };
    }

    @Bean
    public ServerAccessDeniedHandler customAccessDeniedHandler() {
        return (exchange, denied) -> {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().add("Content-Type", "application/json");

            String body = """
                {
                    "error": "access_denied",
                    "message": "Insufficient permissions",
                    "timestamp": "%s",
                    "path": "%s"
                }
                """.formatted(
                    Instant.now(),
                    exchange.getRequest().getPath().value()
            );

            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        };
    }
}