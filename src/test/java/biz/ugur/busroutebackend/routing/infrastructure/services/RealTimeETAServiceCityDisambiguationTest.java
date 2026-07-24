package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.OptionalDouble;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealTimeETAServiceCityDisambiguationTest {

    private static final String ARKADAG_ROUTE_ID = "route-legacy-133";
    private static final String ASHGABAT_ROUTE_ID = "route-legacy-1";

    @Mock
    private VehiclePositionPredictionService predictionService;
    @Mock
    private RouteGeometryCache routeGeometryCache;

    private RealTimeETAService service;

    @BeforeEach
    void setUp() {
        service = new RealTimeETAService(predictionService, routeGeometryCache);
        when(routeGeometryCache.getStopFractionByName(anyString(), anyInt(), anyString()))
                .thenReturn(OptionalDouble.of(0.6));
        when(routeGeometryCache.getTotalDistance(anyString(), anyInt())).thenReturn(10000.0);
        when(predictionService.getActiveStates()).thenReturn(List.of(
                stateOn(ASHGABAT_ROUTE_ID, "veh-ashgabat", 0.55),
                stateOn(ARKADAG_ROUTE_ID, "veh-arkadag", 0.5)));
    }

    private VehiclePredictionState stateOn(String routeId, String vehicleId, double fraction) {
        return VehiclePredictionState.builder()
                .vehicleId(vehicleId)
                .licensePlate(vehicleId)
                .routeNumber("1")
                .routeId(routeId)
                .direction(0)
                .inMotion(true)
                .speedKmh(30.0)
                .smoothedSpeedKmh(30.0)
                .lastGpsFraction(fraction)
                .build();
    }

    @Test
    void routeIdPinsSearchToSingleCityAmongNamesakes() {
        StepVerifier.create(service.findNearestBus(ARKADAG_ROUTE_ID, "1", "Конечная"))
                .assertNext(info -> org.assertj.core.api.Assertions.assertThat(info.vehicleId())
                        .isEqualTo("veh-arkadag"))
                .verifyComplete();
    }

    @Test
    void withoutRouteIdFallsBackToNumberMatchingAllCities() {
        StepVerifier.create(service.findNearestBus(null, "1", "Конечная"))
                .assertNext(info -> org.assertj.core.api.Assertions.assertThat(info.vehicleId())
                        .isEqualTo("veh-ashgabat"))
                .verifyComplete();
    }
}
