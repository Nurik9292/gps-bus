package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.shared.infrastructure.messaging.ReactiveEventBus;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class RouteGeometryCacheEventHandler {

    private final ReactiveEventBus eventBus;
    private final RouteGeometryCache routeGeometryCache;
    private Disposable subscription;

    public RouteGeometryCacheEventHandler(ReactiveEventBus eventBus, RouteGeometryCache routeGeometryCache) {
        this.eventBus = eventBus;
        this.routeGeometryCache = routeGeometryCache;
    }

    @PostConstruct
    public void init() {
        subscription = eventBus.on(RouteGeometryUpdatedEvent.class)
                .concatMap(this::refreshGeometryFor)
                .subscribe(
                        null,
                        err -> log.error("[GPS_PIPELINE] RouteGeometryCache event subscription terminated unexpectedly", err));
        log.info("[GPS_PIPELINE] RouteGeometryCache subscribed to RouteGeometryUpdatedEvent");
    }

    private Mono<Void> refreshGeometryFor(RouteGeometryUpdatedEvent event) {
        String routeNumber = event.getRouteNumber();
        return routeGeometryCache.refreshRoute(routeNumber)
                .onErrorResume(err -> {
                    log.error("[GPS_PIPELINE] Failed to refresh route geometry cache for route {} after geometry change",
                            routeNumber, err);
                    return Mono.empty();
                });
    }

    @PreDestroy
    public void destroy() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
