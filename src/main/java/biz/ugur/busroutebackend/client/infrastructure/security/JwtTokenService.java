package biz.ugur.busroutebackend.client.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;

@Service
@Slf4j
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long accessTokenValidityInHours;
    private final long refreshTokenValidityInDays;

    public JwtTokenService(@Value("${app.security.jwt.secret}") String secret,
                           @Value("${app.security.jwt.access-token-validity-hours-client:24}") long accessTokenValidityInHours,
                           @Value("${app.security.jwt.refresh-token-validity-days-client:30}") long refreshTokenValidityInDays) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenValidityInHours = accessTokenValidityInHours;
        this.refreshTokenValidityInDays = refreshTokenValidityInDays;
    }

    public String generateAccessToken(String clientId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(accessTokenValidityInHours, ChronoUnit.HOURS);

        log.debug("Generating access token for clientId: {}", clientId);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("type", "access")
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(String clientId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(refreshTokenValidityInDays, ChronoUnit.DAYS);

        log.debug("Generating refresh token for clientId: {}", clientId);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("type", "refresh")
                .signWith(secretKey)
                .compact();
    }

    public Claims validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            log.debug("Token validated successfully. Subject: {}, Type: {}",
                    claims.getSubject(), claims.get("type"));
            return claims;

        } catch (Exception e) {
            log.error("Token validation failed: {}", e.getMessage());
            throw e;
        }
    }

    public String getClientIdFromToken(String token) {
        Claims claims = validateToken(token);
        String clientId = claims.getSubject();

        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Token does not contain valid clientId in subject");
        }

        log.debug("Extracted clientId from token: {}", clientId);
        return clientId;
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expiration = validateToken(token).getExpiration();
            boolean expired = expiration.before(new Date());
            log.debug("Token expiration check: expired={}, expiration={}", expired, expiration);
            return expired;
        } catch (Exception e) {
            log.error("Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }


    public boolean isAccessToken(String token) {
        try {
            Claims claims = validateToken(token);
            String type = claims.get("type", String.class);
            return "access".equals(type);
        } catch (Exception e) {
            log.error("Error checking token type: {}", e.getMessage());
            return false;
        }
    }


    public String getTokenId(String token) {
        try {
            return validateToken(token).getId();
        } catch (Exception e) {
            log.error("Error extracting token ID: {}", e.getMessage());
            return null;
        }
    }
}