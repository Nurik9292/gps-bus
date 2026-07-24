package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.PerformanceLogRepository;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.TerminalDepartureEtaService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusStopRealTimeServiceTerminalRowsTest {

    @Mock
    private VehiclePositionPredictionService predictionService;
    @Mock
    private RouteGeometryCache routeGeometryCache;
    @Mock
    private TerminalDepartureEtaService terminalDepartureEtaService;

    private BusStopRealTimeServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new BusStopRealTimeServiceImpl(
                mock(BusStopRepository.class), mock(PerformanceLogRepository.class),
                (ReactiveRedisTemplate<String, Object>) mock(ReactiveRedisTemplate.class),
                mock(DistanceCalculationService.class), new ObjectMapper(),
                new ETAProperties(), predictionService, routeGeometryCache,
                terminalDepartureEtaService);

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId("veh-1")
                .licensePlate("5670 AGJ")
                .routeNumber("23")
                .routeId("route-legacy-31")
                .direction(0)
                .predictedLatitude(37.94)
                .predictedLongitude(58.40)
                .build();
        when(predictionService.getActiveStates()).thenReturn(List.of(state));
        when(routeGeometryCache.getRouteName("route-legacy-31")).thenReturn("Маршрут 23");
        when(routeGeometryCache.getRouteColor("route-legacy-31")).thenReturn("#ff0000");
        when(terminalDepartureEtaService.enabled()).thenReturn(true);
    }

    @Test
    void terminalRowEmittedForStopServedByDepartingDirection() {
        when(terminalDepartureEtaService.departureEtasForVehicle(eq("veh-1"),
                eq("route-legacy-31"), any()))
                .thenReturn(List.of(
                        new TerminalDepartureEtaService.DepartureStopEta("T", "Конечная", 180, 0, 1),
                        new TerminalDepartureEtaService.DepartureStopEta("S1", "Первая", 270, 800, 1)));

        StepVerifier.create(service.terminalDepartureRows("S1"))
                .assertNext(row -> {
                    assertThat(row.getRouteNumber()).isEqualTo("23");
                    assertThat(row.getEstimatedArrivalMinutes()).isEqualTo(5);
                    assertThat(row.getArrivalStatus()).isEqualTo("approaching");
                    assertThat(row.getCurrentStopName()).isEqualTo("На конечной");
                    assertThat(row.getDirection()).isEqualTo(1);
                    assertThat(row.getDistanceMeters()).isEqualTo(800);
                })
                .verifyComplete();
    }

    @Test
    void noRowForForeignStopOrDisabledMode() {
        when(terminalDepartureEtaService.departureEtasForVehicle(anyString(), anyString(), any()))
                .thenReturn(List.of(
                        new TerminalDepartureEtaService.DepartureStopEta("T", "Конечная", 180, 0, 1)));
        StepVerifier.create(service.terminalDepartureRows("S-foreign")).verifyComplete();

        when(terminalDepartureEtaService.enabled()).thenReturn(false);
        StepVerifier.create(service.terminalDepartureRows("T")).verifyComplete();
    }

    @Test
    void rowBeyondMaxEtaMinutesIsDropped() {
        when(terminalDepartureEtaService.departureEtasForVehicle(anyString(), anyString(), any()))
                .thenReturn(List.of(
                        new TerminalDepartureEtaService.DepartureStopEta("T", "Конечная", 4000, 0, 1)));
        StepVerifier.create(service.terminalDepartureRows("T")).verifyComplete();
    }

    @Test
    void terminalRowEmittedEvenWhenLastFixIsStale() {
        VehiclePredictionState staleState = VehiclePredictionState.builder()
                .vehicleId("veh-1")
                .licensePlate("5670 AGJ")
                .routeNumber("23")
                .routeId("route-legacy-31")
                .direction(0)
                .predictedLatitude(37.94)
                .predictedLongitude(58.40)
                .lastGpsUpdate(java.time.Instant.now().minusSeconds(600))
                .build();
        when(predictionService.getActiveStates()).thenReturn(List.of(staleState));
        when(terminalDepartureEtaService.departureEtasForVehicle(eq("veh-1"),
                eq("route-legacy-31"), any()))
                .thenReturn(List.of(
                        new TerminalDepartureEtaService.DepartureStopEta("S1", "Первая", 270, 800, 1)));

        StepVerifier.create(service.terminalDepartureRows("S1"))
                .assertNext(row -> assertThat(row.getCurrentStopName()).isEqualTo("На конечной"))
                .verifyComplete();
    }
}
