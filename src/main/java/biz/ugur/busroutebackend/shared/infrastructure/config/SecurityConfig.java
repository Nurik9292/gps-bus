package biz.ugur.busroutebackend.shared.infrastructure.config;

import biz.ugur.busroutebackend.shared.infrastructure.security.JwtAuthenticationFilter;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtService;
import biz.ugur.busroutebackend.shared.infrastructure.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;


@Slf4j
@Configuration
@EnableWebFluxSecurity
@EnableReactiveMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtService jwtService;
    private final TokenBlacklistService tokenBlacklistService;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .logout(ServerHttpSecurity.LogoutSpec::disable)

                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())

                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((exchange, denied) -> {
                            log.warn("Access denied for path: {} - {}",
                                    exchange.getRequest().getPath().value(), denied.getMessage());
                            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
                            return exchange.getResponse().setComplete();
                        })
                )

                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/actuator/info").permitAll()
                        .pathMatchers(HttpMethod.GET, "/trip-planning/health").permitAll()

                        .pathMatchers(HttpMethod.POST, "/admin/auth/login").permitAll()
                        .pathMatchers(HttpMethod.POST, "/admin/auth/refresh").permitAll()

                        .pathMatchers(HttpMethod.GET, "/public/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/routes/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/stops/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/trips/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/vehicles/**").permitAll()
                        .pathMatchers(HttpMethod.GET, "/trip-planning/**").permitAll()
                        .pathMatchers(HttpMethod.POST, "/trip-planning/**").permitAll()

                        .pathMatchers("/ws/**").permitAll()

                        .pathMatchers(HttpMethod.GET, "/admin/auth/me").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.PUT, "/admin/profile").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/admin/auth/logout").hasRole("ADMIN")
                        .pathMatchers(HttpMethod.POST, "/admin/auth/change-password").hasRole("ADMIN")

                        .pathMatchers("/admin/routes/**").hasRole("ADMIN")
                        .pathMatchers("/admin/stops/**").hasRole("ADMIN")
                        .pathMatchers("/admin/buses/**").hasRole("ADMIN")
                        .pathMatchers("/admin/banners/**").hasRole("ADMIN")
                        .pathMatchers("/admin/cities/**").hasRole("ADMIN")

                        .pathMatchers("/admin/admins/**").hasRole("SUPER_ADMIN")
                        .pathMatchers("/admin/system/**").hasRole("SUPER_ADMIN")
                        .pathMatchers(HttpMethod.GET, "/admin/logs/**").hasRole("SUPER_ADMIN")
                        .pathMatchers(HttpMethod.GET, "/admin/stats/**").hasRole("SUPER_ADMIN")

                        .pathMatchers("/actuator/**").hasRole("SUPER_ADMIN")

                        .anyExchange().denyAll()
                )

                .addFilterBefore(jwtAuthenticationFilter(), SecurityWebFiltersOrder.AUTHENTICATION)

                .build();
    }


    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        return new JwtAuthenticationFilter(jwtService, tokenBlacklistService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("*"));

        configuration.setAllowedHeaders(List.of(
                "Authorization",
                "Content-Type",
                "X-Requested-With",
                "Accept",
                "Origin",
                "Cache-Control",
                "X-Admin-Id"
        ));

        configuration.setAllowedMethods(List.of(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"
        ));

        configuration.setAllowCredentials(true);

        configuration.setMaxAge(3600L);

        configuration.setExposedHeaders(List.of(
                "X-Total-Count",
                "X-Page-Count",
                "Authorization"
        ));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }


    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}