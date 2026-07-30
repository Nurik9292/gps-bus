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
    private final org.springframework.beans.factory.ObjectProvider<biz.ugur.busroutebackend.prediction.shadow.V31RouteLines> v31RouteLinesProvider;
    private final org.springframework.beans.factory.ObjectProvider<biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap.RouteSwapGeoDictionary> routeSwapDictionaryProvider;
    private Disposable subscription;

    public RouteGeometryCacheEventHandler(ReactiveEventBus eventBus, RouteGeometryCache routeGeometryCache,
                                          org.springframework.beans.factory.ObjectProvider<biz.ugur.busroutebackend.prediction.shadow.V31RouteLines> v31RouteLinesProvider,
                                          org.springframework.beans.factory.ObjectProvider<biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap.RouteSwapGeoDictionary> routeSwapDictionaryProvider) {
        this.eventBus = eventBus;
        this.routeGeometryCache = routeGeometryCache;
        this.v31RouteLinesProvider = v31RouteLinesProvider;
        this.routeSwapDictionaryProvider = routeSwapDictionaryProvider;
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
        String routeId = event.getRouteId();
        return routeGeometryCache.refreshRoute(routeId)
                .doOnSuccess(ignored -> {
                    evictV31Topology(event);
                    invalidateRouteSwapDictionary(event);
                })
                .onErrorResume(err -> {
                    log.error("[GPS_PIPELINE] Failed to refresh route geometry cache for route {} after geometry change",
                            routeId, err);
                    return Mono.empty();
                });
    }

    private void evictV31Topology(RouteGeometryUpdatedEvent event) {
        biz.ugur.busroutebackend.prediction.shadow.V31RouteLines lines =
                v31RouteLinesProvider.getIfAvailable();
        if (lines != null) {
            lines.evict(event.getRouteId(), event.getRouteNumber());
            log.info("[GPS_PIPELINE] v31 topology evicted for route {} ({}) after geometry change",
                    event.getRouteId(), event.getRouteNumber());
        }
    }

    private void invalidateRouteSwapDictionary(RouteGeometryUpdatedEvent event) {
        if (routeSwapDictionaryProvider == null) {
            return;
        }
        biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap.RouteSwapGeoDictionary dictionary =
                routeSwapDictionaryProvider.getIfAvailable();
        if (dictionary != null) {
            dictionary.invalidate();
            log.info("[GPS_PIPELINE] route-swap geo dictionary invalidated for route {} ({}) after geometry change",
                    event.getRouteId(), event.getRouteNumber());
        }
    }

    @PreDestroy
    public void destroy() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
        }
    }
}
