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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RealTimeETADirectionTest {

    private static final String ROUTE_ID = "route-legacy-7";
    private static final String STOP_ID = "stop-forward-side";

    @Mock
    private VehiclePositionPredictionService predictionService;
    @Mock
    private RouteGeometryCache routeGeometryCache;

    private RealTimeETAService service;

    @BeforeEach
    void setUp() {
        service = new RealTimeETAService(predictionService, routeGeometryCache);
        when(routeGeometryCache.getTotalDistance(anyString(), anyInt())).thenReturn(10000.0);

        when(routeGeometryCache.getStopFraction(ROUTE_ID, 0, STOP_ID)).thenReturn(OptionalDouble.of(0.60));
        when(routeGeometryCache.getStopFraction(ROUTE_ID, 1, STOP_ID)).thenReturn(OptionalDouble.empty());
    }

    private VehiclePredictionState bus(String vehicleId, int direction, double fraction) {
        return VehiclePredictionState.builder()
                .vehicleId(vehicleId)
                .licensePlate(vehicleId)
                .routeNumber("7")
                .routeId(ROUTE_ID)
                .direction(direction)
                .inMotion(true)
                .speedKmh(30.0)
                .smoothedSpeedKmh(30.0)
                .lastGpsFraction(fraction)
                .build();
    }

    @Test
    void busGoingOppositeDirectionIsNotReportedEvenWhenCloser() {
        when(predictionService.getActiveStates()).thenReturn(List.of(
                bus("veh-opposite", 1, 0.58),
                bus("veh-correct", 0, 0.50)));

        StepVerifier.create(service.findNearestBus(ROUTE_ID, "7", STOP_ID))
                .assertNext(info -> assertThat(info.vehicleId()).isEqualTo("veh-correct"))
                .verifyComplete();
    }

    @Test
    void onlyOppositeDirectionBusesMeansNoArrival() {
        when(predictionService.getActiveStates()).thenReturn(List.of(
                bus("veh-opposite-a", 1, 0.40),
                bus("veh-opposite-b", 1, 0.55)));

        StepVerifier.create(service.findNearestBus(ROUTE_ID, "7", STOP_ID))
                .verifyComplete();
    }

    @Test
    void stopMissingFromRouteGivesNoArrival() {
        when(routeGeometryCache.getStopFraction(eq(ROUTE_ID), anyInt(), eq("stop-of-another-route")))
                .thenReturn(OptionalDouble.empty());
        when(predictionService.getActiveStates()).thenReturn(List.of(bus("veh-correct", 0, 0.50)));

        StepVerifier.create(service.findNearestBus(ROUTE_ID, "7", "stop-of-another-route"))
                .verifyComplete();
    }

    @Test
    void busOnSameDirectionBeforeStopIsReported() {
        when(predictionService.getActiveStates()).thenReturn(List.of(bus("veh-correct", 0, 0.50)));

        StepVerifier.create(service.findNearestBus(ROUTE_ID, "7", STOP_ID))
                .assertNext(info -> {
                    assertThat(info.vehicleId()).isEqualTo("veh-correct");
                    assertThat(info.direction()).isZero();
                    assertThat(info.distanceMeters()).isCloseTo(1000, org.assertj.core.data.Offset.offset(2));
                })
                .verifyComplete();
    }
}
