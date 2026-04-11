package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.shared.infrastructure.messaging.ReactiveEventBus;
import biz.ugur.busroutebackend.transport.domain.event.BusStopCreatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopLocationChangedEvent;
import biz.ugur.busroutebackend.transport.domain.event.BusStopUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;


@Component
@ConditionalOnProperty(name = "routing.dijkstra.enabled", havingValue = "true")
@Slf4j
public class TransitGraphEventHandler {

    private final ReactiveEventBus eventBus;
    private final TransitGraphCache graphCache;
    private final List<Disposable> subscriptions = new ArrayList<>();

    public TransitGraphEventHandler(ReactiveEventBus eventBus, TransitGraphCache graphCache) {
        this.eventBus = eventBus;
        this.graphCache = graphCache;
    }

    @PostConstruct
    public void init() {
        subscriptions.add(eventBus.on(BusStopCreatedEvent.class)
                .subscribe(e -> invalidateAndRebuild("BusStopCreated: " + e.stopName())));

        subscriptions.add(eventBus.on(BusStopUpdatedEvent.class)
                .subscribe(e -> invalidateAndRebuild("BusStopUpdated: " + e.stopId())));

        subscriptions.add(eventBus.on(BusStopLocationChangedEvent.class)
                .subscribe(e -> invalidateAndRebuild("BusStopLocationChanged: " + e.stopId())));

        subscriptions.add(eventBus.on(RouteGeometryUpdatedEvent.class)
                .subscribe(e -> invalidateAndRebuild("RouteGeometryUpdated: " + e.getRouteNumber())));

        log.info("TransitGraphEventHandler subscribed to stop/route change events");
    }

    @PreDestroy
    public void destroy() {
        subscriptions.forEach(s -> { if (!s.isDisposed()) s.dispose(); });
    }

    private void invalidateAndRebuild(String reason) {
        log.info("🔄 Transit graph invalidated ({}), rebuilding in background...", reason);
        graphCache.invalidate();
        graphCache.getGraph().subscribe(
                g -> log.info("✅ Transit graph rebuilt after change: {} stops, {} edges",
                        g.getStopCount(), g.getEdgeCount()),
                e -> log.error("❌ Failed to rebuild transit graph: {}", e.getMessage())
        );
    }
}
