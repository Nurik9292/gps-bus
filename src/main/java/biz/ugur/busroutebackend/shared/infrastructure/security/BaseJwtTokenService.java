package biz.ugur.busroutebackend.shared.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseJwtTokenService<P extends UserDetails> {

    protected final ObjectMapper objectMapper;
    protected final String secret;
    protected final String issuer;

    protected SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Mono<Claims> validateAndExtractClaims(String token) {
        return Mono.fromCallable(() -> {
            try {
                return Jwts.parser()
                        .verifyWith(getSigningKey())
                        .requireIssuer(issuer)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

            } catch (ExpiredJwtException e) {
                log.warn("JWT token expired for subject: {}", e.getClaims().getSubject());
                throw JwtTokenException.fromExpiredJwtException(e, token);
            } catch (UnsupportedJwtException e) {
                log.warn("Unsupported JWT token: {}", e.getMessage());
                throw JwtTokenException.fromUnsupportedJwtException(e, token);
            } catch (MalformedJwtException e) {
                log.warn("Malformed JWT token: {}", e.getMessage());
                throw JwtTokenException.fromMalformedJwtException(e, token);
            } catch (SecurityException e) {
                log.warn("Invalid JWT signature: {}", e.getMessage());
                throw JwtTokenException.fromSecurityException(e, token);
            } catch (IllegalArgumentException e) {
                log.warn("JWT token compact handler are invalid: {}", e.getMessage());
                throw JwtTokenException.invalidToken(token);
            }
        });
    }

    public Mono<Boolean> isTokenExpired(String token) {
        return validateAndExtractClaims(token)
                .map(claims -> claims.getExpiration().before(new Date()))
                .onErrorReturn(true);
    }

    protected Mono<Claims> validateTokenType(Claims claims, String expectedType) {
        String tokenType = claims.get("type", String.class);
        if (!expectedType.equals(tokenType)) {
            return Mono.error(JwtTokenException.invalidTokenType(expectedType, tokenType));
        }
        return Mono.just(claims);
    }

    public abstract Mono<String> generateAccessToken(String subjectId, Object... additionalParams);

    public abstract Mono<String> generateRefreshToken(String subjectId);

    public abstract Mono<P> extractPrincipal(String token);

    public Mono<Boolean> isRefreshToken(String token) {
        return validateAndExtractClaims(token)
                .map(claims -> "refresh".equals(claims.get("type", String.class)))
                .onErrorReturn(false);
    }

    public Mono<String> extractSubjectId(String token) {
        return validateAndExtractClaims(token)
                .map(Claims::getSubject)
                .doOnNext(subjectId -> log.trace("Extracted subject ID from token: {}", subjectId));
    }
}
