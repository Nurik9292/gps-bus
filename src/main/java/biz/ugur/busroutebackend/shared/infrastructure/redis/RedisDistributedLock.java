package biz.ugur.busroutebackend.shared.infrastructure.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@Slf4j
public class RedisDistributedLock {

    /**
     * Lua script for atomic lock release.
     * Checks if the lock value matches before deleting.
     * Returns 1 if released, 0 if not owned or doesn't exist.
     */
    private static final String RELEASE_LOCK_SCRIPT = """
            if redis.call("get", KEYS[1]) == ARGV[1] then
                return redis.call("del", KEYS[1])
            else
                return 0
            end
            """;

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final RedisScript<Long> releaseLockScript;
    private final DistributedLockProperties properties;
    private final String instanceId;

    private final AtomicLong acquiredCount = new AtomicLong(0);
    private final AtomicLong failedCount = new AtomicLong(0);
    private final AtomicLong releasedCount = new AtomicLong(0);

    public RedisDistributedLock(
            ReactiveRedisTemplate<String, Object> redisTemplate,
            DistributedLockProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.releaseLockScript = RedisScript.of(RELEASE_LOCK_SCRIPT, Long.class);
        this.instanceId = properties.getInstanceId() != null
                ? properties.getInstanceId()
                : generateInstanceId();

        log.info("RedisDistributedLock initialized: enabled={}, instanceId={}, defaultTimeout={}",
                properties.isEnabled(), this.instanceId, properties.getDefaultTimeout());
    }

    public Mono<LockHandle> tryAcquire(String lockName) {
        return tryAcquire(lockName, properties.getDefaultTimeout());
    }

    public Mono<LockHandle> tryAcquire(String lockName, Duration timeout) {
        if (!properties.isEnabled()) {
            log.debug("Distributed lock disabled, returning immediate lock for {}", lockName);
            return Mono.just(new LockHandle(lockName, instanceId, true));
        }

        String key = buildKey(lockName);
        String lockValue = buildLockValue();

        return redisTemplate.opsForValue()
                .setIfAbsent(key, lockValue, timeout)
                .flatMap(acquired -> {
                    if (Boolean.TRUE.equals(acquired)) {
                        acquiredCount.incrementAndGet();
                        log.debug("Acquired distributed lock '{}' (key={}, timeout={})",
                                lockName, key, timeout);
                        return Mono.just(new LockHandle(lockName, lockValue, false));
                    } else {
                        failedCount.incrementAndGet();
                        log.debug("Failed to acquire distributed lock '{}' - already held by another instance",
                                lockName);
                        return Mono.empty();
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error acquiring distributed lock '{}': {}", lockName, error.getMessage());
                    failedCount.incrementAndGet();
                    return Mono.empty();
                });
    }

    public Mono<LockHandle> acquireWithRetry(String lockName, Duration lockTimeout) {
        if (!properties.isEnabled()) {
            return Mono.just(new LockHandle(lockName, instanceId, true));
        }

        Duration acquireTimeout = properties.getAcquireTimeout();
        Duration retryInterval = properties.getRetryInterval();
        int maxAttempts = (int) Math.min(
                acquireTimeout.toMillis() / retryInterval.toMillis(),
                Integer.MAX_VALUE
        );

        return tryAcquireWithRetries(lockName, lockTimeout, maxAttempts, retryInterval)
                .doOnNext(handle -> log.debug("Acquired lock '{}' after retry", lockName))
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Failed to acquire lock '{}' after {}ms", lockName, acquireTimeout.toMillis());
                    return Mono.empty();
                }));
    }

    private Mono<LockHandle> tryAcquireWithRetries(String lockName, Duration lockTimeout,
                                                    int remainingAttempts, Duration retryInterval) {
        if (remainingAttempts <= 0) {
            return Mono.empty();
        }

        return tryAcquire(lockName, lockTimeout)
                .switchIfEmpty(Mono.defer(() ->
                        Mono.delay(retryInterval)
                                .then(tryAcquireWithRetries(lockName, lockTimeout,
                                        remainingAttempts - 1, retryInterval))
                ));
    }

    public Mono<Boolean> release(LockHandle handle) {
        if (handle == null) {
            return Mono.just(false);
        }

        if (handle.isLocalOnly()) {
            log.debug("Releasing local-only lock '{}'", handle.lockName());
            return Mono.just(true);
        }

        String key = buildKey(handle.lockName());

        // Use Lua script for atomic check-and-delete
        return redisTemplate.execute(releaseLockScript, List.of(key), List.of(handle.lockValue()))
                .next()
                .map(result -> {
                    if (result != null && result > 0) {
                        releasedCount.incrementAndGet();
                        log.debug("Released distributed lock '{}'", handle.lockName());
                        return true;
                    }
                    log.debug("Lock '{}' already released or expired", handle.lockName());
                    return false;
                })
                .defaultIfEmpty(false)
                .onErrorResume(error -> {
                    log.error("Error releasing distributed lock '{}': {}", handle.lockName(), error.getMessage());
                    return Mono.just(false);
                });
    }

    public Mono<Boolean> extend(LockHandle handle, Duration extension) {
        if (handle == null || handle.isLocalOnly()) {
            return Mono.just(true);
        }

        String key = buildKey(handle.lockName());

        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(currentValue -> {
                    if (handle.lockValue().equals(currentValue)) {
                        return redisTemplate.expire(key, extension)
                                .doOnNext(success -> {
                                    if (Boolean.TRUE.equals(success)) {
                                        log.debug("Extended lock '{}' by {}", handle.lockName(), extension);
                                    }
                                });
                    }
                    return Mono.just(false);
                })
                .defaultIfEmpty(false);
    }

    public Mono<Boolean> isLocked(String lockName) {
        if (!properties.isEnabled()) {
            return Mono.just(false);
        }

        return redisTemplate.hasKey(buildKey(lockName));
    }

    public LockStats getStats() {
        return new LockStats(
                acquiredCount.get(),
                failedCount.get(),
                releasedCount.get()
        );
    }

    public void resetStats() {
        acquiredCount.set(0);
        failedCount.set(0);
        releasedCount.set(0);
    }

    private String buildKey(String lockName) {
        return properties.getKeyPrefix() + lockName;
    }

    private String buildLockValue() {
        return instanceId + ":" + System.currentTimeMillis();
    }

    private String generateInstanceId() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = "unknown";
        }
        return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public record LockHandle(
            String lockName,
            String lockValue,
            boolean isLocalOnly
    ) {}

    public record LockStats(
            long acquired,
            long failed,
            long released
    ) {
        public double successRate() {
            long total = acquired + failed;
            if (total == 0) return 100.0;
            return (double) acquired / total * 100;
        }
    }
}
