package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentLiveStateRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentLiveSnapshot;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Repository
public class RedisSegmentLiveStateRepository implements SegmentLiveStateRepository {

    static final double EMA_ALPHA = 0.4;
    static final Duration LIVE_TTL = Duration.ofMinutes(20);

    private final ReactiveStringRedisTemplate redisTemplate;

    public RedisSegmentLiveStateRepository(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> recordTravel(String fromStopId, String toStopId, double seconds,
                                   Instant observedAt) {
        String key = RedisKeyRegistry.Segments.liveTravel(fromStopId, toStopId);
        return redisTemplate.opsForValue().get(key)
                .map(existing -> blend(existing, seconds, observedAt))
                .defaultIfEmpty(encode(seconds, 1, observedAt))
                .flatMap(value -> redisTemplate.opsForValue().set(key, value, LIVE_TTL))
                .then();
    }

    private String blend(String existing, double seconds, Instant observedAt) {
        String[] parts = existing.split("\\|");
        try {
            double ema = Double.parseDouble(parts[0]);
            long n = Long.parseLong(parts[1]);
            double blended = EMA_ALPHA * seconds + (1 - EMA_ALPHA) * ema;
            return encode(blended, n + 1, observedAt);
        } catch (RuntimeException malformed) {
            return encode(seconds, 1, observedAt);
        }
    }

    private static String encode(double ema, long n, Instant observedAt) {
        return String.format(Locale.ROOT, "%.1f|%d|%d", ema, n, observedAt.toEpochMilli());
    }

    @Override
    public Flux<SegmentLiveSnapshot> scanLiveEdges() {
        String pattern = RedisKeyRegistry.Segments.LIVE_TRAVEL_PREFIX + ":*";
        return redisTemplate.scan(ScanOptions.scanOptions().match(pattern).count(500).build())
                .flatMap(key -> redisTemplate.opsForValue().get(key)
                        .mapNotNull(value -> decode(key, value)), 8);
    }

    private SegmentLiveSnapshot decode(String key, String value) {
        String suffix = key.substring(
                RedisKeyRegistry.Segments.LIVE_TRAVEL_PREFIX.length() + 1);
        int sep = suffix.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        String[] parts = value.split("\\|");
        try {
            return new SegmentLiveSnapshot(
                    suffix.substring(0, sep), suffix.substring(sep + 1),
                    Double.parseDouble(parts[0]), Long.parseLong(parts[1]),
                    Instant.ofEpochMilli(Long.parseLong(parts[2])));
        } catch (RuntimeException malformed) {
            return null;
        }
    }
}
