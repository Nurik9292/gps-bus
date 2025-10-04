package biz.ugur.busroutebackend.shared.infrastructure.security;

import biz.ugur.busroutebackend.admin.infrastructure.security.AdminJwtProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final ReactiveStringRedisTemplate redisTemplate;
    private final AdminJwtProperties jwtProperties;

    private static final String ACCESS_BLACKLIST_PREFIX = "blacklist:access:";
    private static final String REFRESH_BLACKLIST_PREFIX = "blacklist:refresh:";
    private static final String BLACKLIST_VALUE = "blacklisted";

    public Mono<Void> blacklistAccessToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("Attempted to blacklist null or empty access token");
            return Mono.empty();
        }

        String key = ACCESS_BLACKLIST_PREFIX + token;
        Duration ttl = jwtProperties.getAccessTokenExpiration();

        log.debug("Blacklisting access token with key: {} for duration: {}", key, ttl);

        return redisTemplate.opsForValue()
                .set(key, BLACKLIST_VALUE, ttl)
                .doOnSuccess(result -> {
                    if (Boolean.TRUE.equals(result)) {
                        log.debug("Access token successfully blacklisted");
                    } else {
                        log.warn("Failed to blacklist access token");
                    }
                })
                .doOnError(error -> log.error("Error blacklisting access token: {}", error.getMessage(), error))
                .then();
    }

    public Mono<Void> blacklistRefreshToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("Attempted to blacklist null or empty refresh token");
            return Mono.empty();
        }

        String key = REFRESH_BLACKLIST_PREFIX + token;
        Duration ttl = jwtProperties.getRefreshTokenExpiration();

        log.debug("Blacklisting refresh token with key: {} for duration: {}", key, ttl);

        return redisTemplate.opsForValue()
                .set(key, BLACKLIST_VALUE, ttl)
                .doOnSuccess(result -> {
                    if (Boolean.TRUE.equals(result)) {
                        log.debug("Refresh token successfully blacklisted");
                    } else {
                        log.warn("Failed to blacklist refresh token");
                    }
                })
                .doOnError(error -> log.error("Error blacklisting refresh token: {}", error.getMessage(), error))
                .then();
    }

    public Mono<Boolean> isAccessTokenBlacklisted(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.debug("Checking blacklist status for null/empty access token - returning false");
            return Mono.just(false);
        }

        String key = ACCESS_BLACKLIST_PREFIX + token;

        return redisTemplate.hasKey(key)
                .doOnNext(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.debug("Access token found in blacklist");
                    } else {
                        log.trace("Access token not in blacklist");
                    }
                })
                .doOnError(error -> log.error("Error checking access token blacklist status: {}", error.getMessage(), error))
                .onErrorReturn(false);
    }

    public Mono<Boolean> isRefreshTokenBlacklisted(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.debug("Checking blacklist status for null/empty refresh token - returning false");
            return Mono.just(false);
        }

        String key = REFRESH_BLACKLIST_PREFIX + token;

        return redisTemplate.hasKey(key)
                .doOnNext(isBlacklisted -> {
                    if (isBlacklisted) {
                        log.debug("Refresh token found in blacklist");
                    } else {
                        log.trace("Refresh token not in blacklist");
                    }
                })
                .doOnError(error -> log.error("Error checking refresh token blacklist status: {}", error.getMessage(), error))
                .onErrorReturn(false); // В случае ошибки Redis считаем что токен не в blacklist
    }

    public Mono<Void> permanentlyBlacklistToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            log.warn("Attempted to permanently blacklist null or empty token");
            return Mono.empty();
        }

        String key = "blacklist:permanent:" + token;

        log.warn("Permanently blacklisting token: {}", key);

        return redisTemplate.opsForValue()
                .set(key, BLACKLIST_VALUE)
                .doOnSuccess(result -> log.warn("Token permanently blacklisted"))
                .doOnError(error -> log.error("Error permanently blacklisting token: {}", error.getMessage(), error))
                .then();
    }

    public Mono<Boolean> isPermanentlyBlacklisted(String token) {
        if (token == null || token.trim().isEmpty()) {
            return Mono.just(false);
        }

        String key = "blacklist:permanent:" + token;

        return redisTemplate.hasKey(key)
                .doOnError(error -> log.error("Error checking permanent blacklist: {}", error.getMessage(), error))
                .onErrorReturn(false);
    }

    public Mono<Void> blacklistAllUserTokens(String adminId) {
        if (adminId == null || adminId.trim().isEmpty()) {
            log.warn("Attempted to blacklist tokens for null/empty adminId");
            return Mono.empty();
        }

        log.warn("Blacklisting all tokens for admin: {}", adminId);

        // В реальной реализации здесь можно было бы хранить mapping
        // adminId -> список активных токенов, но для простоты
        // используем pattern-based blacklisting
        String pattern = "blacklist:user:" + adminId;

        return redisTemplate.opsForValue()
                .set(pattern, BLACKLIST_VALUE, Duration.ofDays(30)) // 30 дней blacklist
                .doOnSuccess(result -> log.warn("All tokens blacklisted for admin: {}", adminId))
                .doOnError(error -> log.error("Error blacklisting all user tokens: {}", error.getMessage(), error))
                .then();
    }

    public Mono<BlacklistStats> getBlacklistStats() {
        return Mono.zip(
                        countKeys(ACCESS_BLACKLIST_PREFIX + "*"),
                        countKeys(REFRESH_BLACKLIST_PREFIX + "*"),
                        countKeys("blacklist:permanent:*")
                )
                .map(tuple -> new BlacklistStats(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()
                ))
                .doOnNext(stats -> log.debug("Blacklist stats: {}", stats))
                .onErrorReturn(new BlacklistStats(0L, 0L, 0L));
    }

    private Mono<Long> countKeys(String pattern) {
        return redisTemplate.keys(pattern)
                .count()
                .onErrorReturn(0L);
    }

    public Mono<Long> cleanupExpiredTokens() {
        log.info("Manual cleanup of expired blacklisted tokens");

        return Mono.just(0L)
                .doOnNext(count -> log.info("Cleaned up {} expired blacklisted tokens", count));
    }

    public Mono<Boolean> healthCheck() {
        String testKey = "blacklist:health:test";
        String testValue = "test";

        return redisTemplate.opsForValue()
                .set(testKey, testValue, Duration.ofSeconds(1))
                .then(redisTemplate.hasKey(testKey))
                .then(redisTemplate.delete(testKey))
                .map(deleted -> deleted > 0)
                .doOnNext(healthy -> {
                    if (healthy) {
                        log.debug("TokenBlacklistService health check passed");
                    } else {
                        log.warn("TokenBlacklistService health check failed");
                    }
                })
                .onErrorReturn(false);
    }

    public record BlacklistStats(
            long accessTokensBlacklisted,
            long refreshTokensBlacklisted,
            long permanentlyBlacklisted
    ) {

    }
}