package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteGeometryCacheStopFractionTest {

    private static final String ROUTE = "38";
    private static final String STRAIGHT_1KM_NORTH_WKT = "LINESTRING(58.0 37.0, 58.0 37.009)";

    @Mock
    private BusRouteRepository busRouteRepository;

    private RouteGeometryCache cache;

    @BeforeEach
    void setUp() {
        cache = new RouteGeometryCache(busRouteRepository, new MapMatchingService(new PredictionProperties()));
    }

    @Test
    void stopFractionComesFromGeometricSnapNotLegacyDistanceColumn() {
        givenRouteWithStops(stopAt("stop-mid", 37.0045, 58.0, 900));

        OptionalDouble fraction = cache.getStopFraction(ROUTE, 0, "stop-mid");

        assertThat(fraction).isPresent();
        assertThat(fraction.getAsDouble()).isCloseTo(0.5, within(0.01));
    }

    @Test
    void stopTooFarFromPolylineFallsBackToLegacyDistanceRatio() {
        givenRouteWithStops(stopAt("stop-far", 37.0045, 58.1, 900));

        OptionalDouble fraction = cache.getStopFraction(ROUTE, 0, "stop-far");

        assertThat(fraction).isPresent();
        assertThat(fraction.getAsDouble()).isCloseTo(0.9, within(0.01));
    }

    @Test
    void stopFractionsArrayIsSortedByGeometricPositionDespiteShuffledLegacyDistances() {
        givenRouteWithStops(
                stopAt("stop-late", 37.008, 58.0, 500),
                stopAt("stop-early", 37.001, 58.0, 8000));

        double[] fractions = cache.getStopFractions(ROUTE, 0);

        assertThat(fractions).hasSize(2);
        assertThat(fractions[0]).isCloseTo(0.111, within(0.01));
        assertThat(fractions[1]).isCloseTo(0.889, within(0.01));
    }

    private void givenRouteWithStops(RouteStopInfo... stops) {
        BusRoute route = BusRoute.builder()
                .id(biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId.of(ROUTE))
                .routeNumber(ROUTE)
                .isActive(true)
                .routeGeometryForward(STRAIGHT_1KM_NORTH_WKT)
                .build();
        when(busRouteRepository.findById(biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId.of(ROUTE)))
                .thenReturn(Mono.just(route));
        when(busRouteRepository.getRouteStopsInfoByRouteId(ROUTE, 0)).thenReturn(Flux.just(stops));
        when(busRouteRepository.getRouteStopsInfoByRouteId(ROUTE, 1)).thenReturn(Flux.empty());

        StepVerifier.create(cache.refreshRoute(ROUTE)).verifyComplete();
    }

    private RouteStopInfo stopAt(String stopId, double lat, double lon, int legacyDistanceFromStartMeters) {
        return new RouteStopInfo(
                stopId,
                "Stop " + stopId,
                "ST-" + stopId,
                1,
                0,
                2,
                legacyDistanceFromStartMeters,
                BigDecimal.valueOf(lat),
                BigDecimal.valueOf(lon),
                false);
    }
}
