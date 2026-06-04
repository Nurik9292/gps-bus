package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.shared.infrastructure.messaging.ReactiveEventBus;
import biz.ugur.busroutebackend.transport.domain.event.BusStopCreatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopLocationChangedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
public class RaptorTimetableEventHandler {

    private final ReactiveEventBus eventBus;
    private final RaptorTimetableCache cache;
    private final List<Disposable> subscriptions = new ArrayList<>();

    public RaptorTimetableEventHandler(ReactiveEventBus eventBus, RaptorTimetableCache cache) {
        this.eventBus = eventBus;
        this.cache = cache;
    }

    @PostConstruct
    public void init() {
        subscriptions.add(eventBus.on(BusStopCreatedEvent.class)
                .subscribe(e -> invalidate("BusStopCreated: " + e.stopName())));
        subscriptions.add(eventBus.on(BusStopUpdatedEvent.class)
                .subscribe(e -> invalidate("BusStopUpdated: " + e.stopId())));
        subscriptions.add(eventBus.on(BusStopLocationChangedEvent.class)
                .subscribe(e -> invalidate("BusStopLocationChanged: " + e.stopId())));
        subscriptions.add(eventBus.on(RouteGeometryUpdatedEvent.class)
                .subscribe(e -> invalidate("RouteGeometryUpdated: " + e.getRouteNumber())));

        log.info("[RAPTOR_CACHE] subscribed to BusStop+Route change events");
    }

    @PreDestroy
    public void destroy() {
        subscriptions.forEach(s -> {
            if (!s.isDisposed()) s.dispose();
        });
    }

    private void invalidate(String reason) {
        log.info("[RAPTOR_CACHE] invalidating, reason={}", reason);
        cache.invalidate();
        cache.getTimetable().subscribe(
                tt -> log.info("[RAPTOR_CACHE] rebuilt after change: routes={} transfers={}",
                        tt.routeCount(), tt.transferCount()),
                err -> log.error("[RAPTOR_CACHE] rebuild after change failed", err));
    }
}
