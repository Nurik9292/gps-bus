package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.shared.infrastructure.messaging.ReactiveEventBus;
import biz.ugur.busroutebackend.transport.domain.event.RouteGeometryUpdatedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteGeometryCacheEventHandlerTest {

    @Mock
    ReactiveEventBus eventBus;

    @Mock
    RouteGeometryCache routeGeometryCache;

    @InjectMocks
    RouteGeometryCacheEventHandler handler;

    private RouteGeometryUpdatedEvent geometryUpdatedFor(String routeNumber) {
        return new RouteGeometryUpdatedEvent(
                "route-id-" + routeNumber, routeNumber, "Route " + routeNumber,
                100, 12000, 98, 11800);
    }

    @Test
    void refreshesGeometryCacheForRouteOnGeometryUpdatedEvent() {
        when(eventBus.on(RouteGeometryUpdatedEvent.class))
                .thenReturn(Flux.just(geometryUpdatedFor("47")));
        when(routeGeometryCache.refreshRoute("route-id-47")).thenReturn(Mono.empty());

        handler.init();

        verify(routeGeometryCache).refreshRoute("route-id-47");
    }

    @Test
    void subscriptionSurvivesAndProcessesNextEventWhenOneRefreshFails() {
        when(eventBus.on(RouteGeometryUpdatedEvent.class))
                .thenReturn(Flux.just(geometryUpdatedFor("3"), geometryUpdatedFor("29")));
        when(routeGeometryCache.refreshRoute("route-id-3"))
                .thenReturn(Mono.error(new RuntimeException("db unavailable")));
        when(routeGeometryCache.refreshRoute("route-id-29")).thenReturn(Mono.empty());

        handler.init();

        verify(routeGeometryCache).refreshRoute("route-id-3");
        verify(routeGeometryCache).refreshRoute("route-id-29");
    }
}
