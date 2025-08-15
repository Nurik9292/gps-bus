package biz.ugur.busroutebackend.client.infrastructure.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Map;

@Service
public class JwtTokenService {

    private final SecretKey secretKey;
    private final long accessTokenValidityInHours;
    private final long refreshTokenValidityInDays;

    public JwtTokenService(@Value("${app.security.jwt.secret}") String secret,
                           @Value("${app.security.jwt.access-token-validity-hours:24}") long accessTokenValidityInHours,
                           @Value("${app.security.jwt.refresh-token-validity-days:30}") long refreshTokenValidityInDays) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenValidityInHours = accessTokenValidityInHours;
        this.refreshTokenValidityInDays = refreshTokenValidityInDays;
    }

    public String generateAccessToken(String clientId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(accessTokenValidityInHours, ChronoUnit.HOURS);


        return Jwts.builder()
                .id(clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("type", "access")
                .signWith(Keys.hmacShaKeyFor(secretKey.getEncoded()))
                .compact();
    }

    public String generateRefreshToken(String clientId) {
        Instant now = Instant.now();
        Instant expiration = now.plus(refreshTokenValidityInDays, ChronoUnit.DAYS);

        return Jwts.builder()
                .subject(clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .claim("type", "refresh")
                .signWith(Keys.hmacShaKeyFor(secretKey.getEncoded()))
                .compact();
    }

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getClientIdFromToken(String token) {
        return validateToken(token).getSubject();
    }

    public boolean isTokenExpired(String token) {
        return validateToken(token).getExpiration().before(new Date());
    }
}