package biz.ugur.busroutebackend.shared.infrastructure.security;

import biz.ugur.busroutebackend.admin.domain.valueobjects.AdminId;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;
    private final ObjectMapper objectMapper;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes());
    }

    public Mono<String> generateAccessToken(AdminId adminId, String username, Set<String> roles, boolean isSuperAdmin) {
        return Mono.fromCallable(() -> {
            try {
                Instant now = Instant.now();
                Instant expiration = now.plus(jwtProperties.accessTokenExpiration());

                Map<String, Object> claims = Map.of(
                        "sub", adminId.getValue(),
                        "username", username,
                        "roles", objectMapper.writeValueAsString(roles),
                        "isSuperAdmin", isSuperAdmin,
                        "type", "access"
                );

                return Jwts.builder()
                        .claims(claims)
                        .issuer(jwtProperties.issuer())
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(expiration))
                        .signWith(getSigningKey())
                        .compact();

            } catch (JsonProcessingException e) {
                throw new JwtTokenException("Failed to serialize roles", e);
            }
        });
    }

    public Mono<String> generateRefreshToken(AdminId adminId) {
        return Mono.fromCallable(() -> {
            Instant now = Instant.now();
            Instant expiration = now.plus(jwtProperties.refreshTokenExpiration());

            return Jwts.builder()
                    .subject(adminId.getValue())
                    .claim("type", "refresh")
                    .issuer(jwtProperties.issuer())
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(expiration))
                    .signWith(getSigningKey())
                    .compact();
        });
    }

    public Mono<Claims> validateAndExtractClaims(String token) {
        return Mono.fromCallable(() -> {
            try {
                return Jwts.parser()
                        .verifyWith(getSigningKey())
                        .requireIssuer(jwtProperties.issuer())
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

            } catch (ExpiredJwtException e) {
                log.warn("JWT token expired: {}", e.getMessage());
                throw new JwtTokenException("Token expired", e);
            } catch (UnsupportedJwtException e) {
                log.warn("Unsupported JWT token: {}", e.getMessage());
                throw new JwtTokenException("Unsupported token", e);
            } catch (MalformedJwtException e) {
                log.warn("Malformed JWT token: {}", e.getMessage());
                throw new JwtTokenException("Malformed token", e);
            } catch (SecurityException e) {
                log.warn("Invalid JWT signature: {}", e.getMessage());
                throw new JwtTokenException("Invalid signature", e);
            } catch (IllegalArgumentException e) {
                log.warn("JWT token compact of handler are invalid: {}", e.getMessage());
                throw new JwtTokenException("Invalid token", e);
            }
        });
    }

    public Mono<AdminPrincipal> extractAdminPrincipal(String token) {
        return validateAndExtractClaims(token)
                .handle((claims, sink) -> {
                    try {
                        String adminIdStr = claims.getSubject();
                        String username = claims.get("username", String.class);
                        String rolesJson = claims.get("roles", String.class);
                        Boolean isSuperAdmin = claims.get("isSuperAdmin", Boolean.class);
                        String tokenType = claims.get("type", String.class);

                        if (!"access".equals(tokenType)) {
                            sink.error(new JwtTokenException("Invalid token type: " + tokenType));
                            return;
                        }

                        @SuppressWarnings("unchecked")
                        Set<String> roles = objectMapper.readValue(rolesJson, Set.class);

                        sink.next(new AdminPrincipal(
                                AdminId.of(adminIdStr),
                                username,
                                roles,
                                Boolean.TRUE.equals(isSuperAdmin)
                        ));

                    } catch (JsonProcessingException e) {
                        sink.error(new JwtTokenException("Failed to parse token claims", e));
                    }
                });
    }

    public Mono<Boolean> isRefreshToken(String token) {
        return validateAndExtractClaims(token)
                .map(claims -> "refresh".equals(claims.get("type", String.class)))
                .onErrorReturn(false);
    }

    public Mono<Boolean> isTokenExpired(String token) {
        return validateAndExtractClaims(token)
                .map(claims -> claims.getExpiration().before(new Date()))
                .onErrorReturn(true);
    }

    public static class JwtTokenException extends RuntimeException {
        public JwtTokenException(String message) {
            super(message);
        }

        public JwtTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}