package biz.ugur.busroutebackend.interfaces.rest.admin.V1.controller;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRouteControllerRefreshCacheTest {

    @Mock
    private BusRouteRepository busRouteRepository;
    @Mock
    private RouteGeometryCache routeGeometryCache;

    private AdminRouteController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminRouteController(null, null, null, null, null, null, null,
                busRouteRepository, routeGeometryCache, null, null, null);
    }

    @Test
    void refreshWithCityResolvesRouteIdBeforeCacheRefresh() {
        when(busRouteRepository.findByRouteNumberAndCityId("2", "city-006")).thenReturn(Mono.just(
                BusRoute.builder().id(new BusRouteId("route-legacy-135")).routeNumber("2").build()));
        when(routeGeometryCache.refreshRoute("route-legacy-135")).thenReturn(Mono.empty());

        StepVerifier.create(controller.refreshRouteCache("2", "city-006"))
                .expectNextCount(1)
                .verifyComplete();

        verify(routeGeometryCache).refreshRoute("route-legacy-135");
    }

    @Test
    void refreshWithoutCityKeepsLegacyNumberBehaviour() {
        when(routeGeometryCache.refreshRoute("34")).thenReturn(Mono.empty());

        StepVerifier.create(controller.refreshRouteCache("34", null))
                .expectNextCount(1)
                .verifyComplete();

        verify(routeGeometryCache).refreshRoute("34");
    }

    @Test
    void refreshWithUnknownCityPairFallsBackToNumber() {
        when(busRouteRepository.findByRouteNumberAndCityId(anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(routeGeometryCache.refreshRoute("2")).thenReturn(Mono.empty());

        StepVerifier.create(controller.refreshRouteCache("2", "city-404"))
                .expectNextCount(1)
                .verifyComplete();

        verify(routeGeometryCache).refreshRoute("2");
    }
}
