package biz.ugur.busroutebackend.admin.infrastructure.security;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import biz.ugur.busroutebackend.shared.infrastructure.security.BaseJwtTokenService;
import biz.ugur.busroutebackend.shared.infrastructure.security.JwtTokenException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class AdminJwtTokenService extends BaseJwtTokenService<AdminPrincipal> {

    private final AdminJwtProperties adminJwtProperties;

    public AdminJwtTokenService(ObjectMapper objectMapper, AdminJwtProperties adminJwtProperties) {
        super(objectMapper, adminJwtProperties.getSecret(), adminJwtProperties.getIssuer());
        this.adminJwtProperties = adminJwtProperties;
    }

    @Override
    public Mono<String> generateAccessToken(String subjectId, Object... additionalParams) {
        if (additionalParams.length < 3) {
            return Mono.error(new IllegalArgumentException(
                    "AdminJwtTokenService requires username, roles, and isSuperAdmin parameters"));
        }

        String username = (String) additionalParams[0];
        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) additionalParams[1];
        boolean isSuperAdmin = (boolean) additionalParams[2];

        return generateAccessToken(AdminId.of(subjectId), username, roles, isSuperAdmin);
    }

    public Mono<String> generateAccessToken(AdminId adminId, String username, Set<String> roles, boolean isSuperAdmin) {
        return Mono.fromCallable(() -> {
            try {
                Instant now = Instant.now();
                Instant expiration = now.plus(adminJwtProperties.getAccessTokenExpiration());

                Map<String, Object> claims = Map.of(
                        "sub", adminId.getValue(),
                        "username", username,
                        "roles", objectMapper.writeValueAsString(roles),
                        "isSuperAdmin", isSuperAdmin,
                        "type", "access"
                );

                return Jwts.builder()
                        .claims(claims)
                        .issuer(issuer)
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiration))
                        .signWith(getSigningKey())
                        .compact();

            } catch (JsonProcessingException e) {
                log.error("Failed to serialize roles for admin: {}", username, e);
                throw JwtTokenException.tokenGenerationError("Failed to serialize roles", e);
            } catch (Exception e) {
                log.error("Failed to generate access token for admin: {}", username, e);
                throw JwtTokenException.tokenGenerationError("Failed to generate access token", e);
            }
        });
    }

    @Override
    public Mono<String> generateRefreshToken(String subjectId) {
        return generateRefreshToken(AdminId.of(subjectId));
    }

    public Mono<String> generateRefreshToken(AdminId adminId) {
        return Mono.fromCallable(() -> {
            try {
                Instant now = Instant.now();
                Instant expiration = now.plus(adminJwtProperties.getRefreshTokenExpiration());

                return Jwts.builder()
                        .subject(adminId.getValue())
                        .claim("type", "refresh")
                        .issuer(issuer)
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiration))
                        .signWith(getSigningKey())
                        .compact();

            } catch (Exception e) {
                log.error("Failed to generate refresh token for admin: {}", adminId.getValue(), e);
                throw JwtTokenException.tokenGenerationError("Failed to generate refresh token", e);
            }
        });
    }

    @Override
    public Mono<AdminPrincipal> extractPrincipal(String token) {
        return validateAndExtractClaims(token)
                .flatMap(claims -> validateTokenType(claims, "access"))
                .flatMap(claims -> {
                    try {
                        String adminIdStr = claims.getSubject();
                        String username = claims.get("username", String.class);
                        String rolesJson = claims.get("roles", String.class);
                        Boolean isSuperAdmin = claims.get("isSuperAdmin", Boolean.class);

                        if (adminIdStr == null || username == null || rolesJson == null) {
                            log.error("Missing required claims - adminId: {}, username: {}, rolesJson: {}",
                                    adminIdStr, username, rolesJson);
                            return Mono.error(JwtTokenException.claimsParsingError("Missing required claims in token", null));
                        }

                        @SuppressWarnings("unchecked")
                        Set<String> roles = objectMapper.readValue(rolesJson, Set.class);

                        AdminPrincipal principal = new AdminPrincipal(
                                AdminId.of(adminIdStr),
                                username,
                                roles,
                                Boolean.TRUE.equals(isSuperAdmin)
                        );

                        return Mono.just(principal);

                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse roles from token claims: {}", e.getMessage(), e);
                        return Mono.error(JwtTokenException.claimsParsingError("Failed to parse roles from token", e));
                    } catch (Exception e) {
                        log.warn("Failed to extract admin principal from token: {}", e.getMessage(), e);
                        return Mono.error(JwtTokenException.claimsParsingError("Failed to extract admin principal", e));
                    }
                })
                .doOnError(error -> log.error("Principal extraction failed with error: {}", error.getMessage(), error));
    }

    public Mono<String> extractUsername(String token) {
        return validateAndExtractClaims(token)
                .map(claims -> claims.get("username", String.class))
                .doOnNext(username -> log.trace("Extracted username from token: {}", username));
    }

    public Mono<AdminId> extractAdminId(String token) {
        return validateAndExtractClaims(token)
                .map(claims -> AdminId.of(claims.getSubject()))
                .doOnNext(adminId -> log.trace("Extracted admin ID from token: {}", adminId.getValue()));
    }
}
