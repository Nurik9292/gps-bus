package biz.ugur.busroutebackend.integration.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
@Slf4j
public class ApiTokenRateLimiter {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:external_service:";
    private static final DateTimeFormatter MINUTE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    public Mono<Boolean> checkRateLimit(String externalServiceId, int limitPerMinute) {
        String currentMinute = LocalDateTime.now().format(MINUTE_FORMATTER);
        String key = RATE_LIMIT_KEY_PREFIX + externalServiceId + ":" + currentMinute;

        return redisTemplate.opsForValue()
                .increment(key)
                .flatMap(count -> {
                    if (count == 1) {
                        return redisTemplate.expire(key, Duration.ofMinutes(2))
                                .thenReturn(true);
                    }

                    if (count > limitPerMinute) {
                        log.warn("Rate limit exceeded for service: {} - {}/{} requests",
                                externalServiceId, count, limitPerMinute);
                        return Mono.just(false);
                    }

                    return Mono.just(true);
                })
                .onErrorResume(error -> {
                    log.error("Error checking rate limit for service: {}", externalServiceId, error);
                    return Mono.just(true);
                });
    }

    public Mono<Long> getCurrentCount(String externalServiceId) {
        String currentMinute = LocalDateTime.now().format(MINUTE_FORMATTER);
        String key = RATE_LIMIT_KEY_PREFIX + externalServiceId + ":" + currentMinute;

        return redisTemplate.opsForValue()
                .get(key)
                .map(Long::parseLong)
                .defaultIfEmpty(0L)
                .onErrorReturn(0L);
    }

    public Mono<Void> resetRateLimit(String externalServiceId) {
        String pattern = RATE_LIMIT_KEY_PREFIX + externalServiceId + ":*";

        return redisTemplate.keys(pattern)
                .flatMap(redisTemplate::delete)
                .then()
                .doOnSuccess(v -> log.info("Reset rate limit for service: {}", externalServiceId));
    }
}
