package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.routing.domain.model.raptor.RaptorTimetable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Component
@Slf4j
public class RaptorTimetableCache {

    private final RaptorTimetableBuilder builder;
    private final Duration ttl;

    private final AtomicReference<RaptorTimetable> ref = new AtomicReference<>();
    private volatile Mono<RaptorTimetable> buildingMono = null;

    public RaptorTimetableCache(RaptorTimetableBuilder builder,
                                 @Value("${routing.raptor.cache-ttl-minutes:30}") int ttlMinutes) {
        this.builder = builder;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        log.info("[RAPTOR_CACHE] preload on startup (ttl={}m)", ttl.toMinutes());
        getTimetable().subscribe(
                tt -> log.info("[RAPTOR_CACHE] preload done: routes={} stops={} transfers={}",
                        tt.routeCount(), tt.stopCount(), tt.transferCount()),
                err -> log.error("[RAPTOR_CACHE] preload failed", err)
        );
    }

    public Mono<RaptorTimetable> getTimetable() {
        RaptorTimetable existing = ref.get();
        if (existing != null && !isExpired(existing)) {
            return Mono.just(existing);
        }
        return rebuild();
    }

    public void invalidate() {
        ref.set(null);
        log.info("[RAPTOR_CACHE] invalidated");
    }

    public boolean hasFreshSnapshot() {
        RaptorTimetable existing = ref.get();
        return existing != null && !isExpired(existing);
    }

    private boolean isExpired(RaptorTimetable timetable) {
        return Duration.between(timetable.builtAt(), Instant.now()).compareTo(ttl) >= 0;
    }

    private synchronized Mono<RaptorTimetable> rebuild() {
        RaptorTimetable existing = ref.get();
        if (existing != null && !isExpired(existing)) {
            return Mono.just(existing);
        }
        if (buildingMono != null) {
            log.debug("[RAPTOR_CACHE] rebuild in progress, reusing shared Mono");
            return buildingMono;
        }

        log.info("[RAPTOR_CACHE] rebuilding (expired or first load)");
        buildingMono = builder.build()
                .doOnSuccess(tt -> {
                    ref.set(tt);
                    synchronized (RaptorTimetableCache.this) {
                        buildingMono = null;
                    }
                })
                .doOnError(err -> {
                    log.error("[RAPTOR_CACHE] rebuild failed", err);
                    synchronized (RaptorTimetableCache.this) {
                        buildingMono = null;
                    }
                })
                .cache();

        return buildingMono;
    }
}
