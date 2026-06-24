package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.shared.infrastructure.messaging.ReactiveEventBus;
import biz.ugur.busroutebackend.transport.domain.event.BusStopCreatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopLocationChangedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class RaptorTimetableEventHandler {

    private final ReactiveEventBus eventBus;
    private final RaptorTimetableCache cache;
    private final RaptorTimetableGenerator timetableGenerator;
    private final RaptorTransferGenerator transferGenerator;
    private final Duration regenerationDebounce;
    private final List<Disposable> subscriptions = new ArrayList<>();

    public RaptorTimetableEventHandler(ReactiveEventBus eventBus,
                                       RaptorTimetableCache cache,
                                       RaptorTimetableGenerator timetableGenerator,
                                       RaptorTransferGenerator transferGenerator,
                                       @Value("${routing.raptor.regeneration-debounce-seconds:10}") int debounceSeconds) {
        this.eventBus = eventBus;
        this.cache = cache;
        this.timetableGenerator = timetableGenerator;
        this.transferGenerator = transferGenerator;
        this.regenerationDebounce = Duration.ofSeconds(debounceSeconds);
    }

    @PostConstruct
    public void init() {
        Flux<String> stopAndRouteEdits = Flux.merge(
                eventBus.on(BusStopCreatedEvent.class).map(e -> "BusStopCreated: " + e.stopName()),
                eventBus.on(BusStopUpdatedEvent.class).map(e -> "BusStopUpdated: " + e.stopId()),
                eventBus.on(BusStopLocationChangedEvent.class).map(e -> "BusStopLocationChanged: " + e.stopId()),
                eventBus.on(RouteGeometryUpdatedEvent.class).map(e -> "RouteGeometryUpdated: " + e.getRouteNumber()));

        subscriptions.add(stopAndRouteEdits
                .sampleTimeout(reason -> Mono.delay(regenerationDebounce))
                .concatMap(this::regenerateAndInvalidate)
                .subscribe());

        log.info("[RAPTOR_CACHE] subscribed to BusStop+Route change events (regenerate debounce={}s)",
                regenerationDebounce.toSeconds());
    }

    @PreDestroy
    public void destroy() {
        subscriptions.forEach(s -> {
            if (!s.isDisposed()) s.dispose();
        });
    }

    Mono<Void> regenerateAndInvalidate(String reason) {
        log.info("[RAPTOR_CACHE] regenerating timetable, reason={}", reason);
        return timetableGenerator.regenerate()
                .then(Mono.defer(transferGenerator::regenerate))
                .doOnSuccess(result -> {
                    cache.invalidate();
                    log.info("[RAPTOR_CACHE] timetable regenerated + cache invalidated, reason={}", reason);
                })
                .onErrorResume(error -> {
                    log.error("[RAPTOR_CACHE] timetable regeneration failed, reason={}", reason, error);
                    return Mono.empty();
                })
                .then();
    }
}
