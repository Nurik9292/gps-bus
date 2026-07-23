package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.PerformanceLogRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.TerminalDepartureEtaService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BusStopRealTimeServiceStaleFixFilterTest {

    @Mock
    private VehiclePositionPredictionService predictionService;
    @Mock
    private RouteGeometryCache routeGeometryCache;
    @Mock
    private TerminalDepartureEtaService terminalDepartureEtaService;
    @Mock
    private BusStopRepository busStopRepository;

    private BusStopRealTimeServiceImpl service;
    private BusStop stop;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = new BusStopRealTimeServiceImpl(
                busStopRepository, mock(PerformanceLogRepository.class),
                (ReactiveRedisTemplate<String, Object>) mock(ReactiveRedisTemplate.class),
                mock(DistanceCalculationService.class), new ObjectMapper(),
                new ETAProperties(), predictionService, routeGeometryCache,
                terminalDepartureEtaService);

        stop = BusStop.restore(BusStopId.of("stop-1"), "Остановка", null, null, null,
                new BigDecimal("37.95"), new BigDecimal("58.38"), true, false,
                "city-001", null, null, 0L);
        when(routeGeometryCache.getStopFraction("route-1", 0, "stop-1"))
                .thenReturn(OptionalDouble.of(0.6));
        when(routeGeometryCache.getRouteName("route-1")).thenReturn("Маршрут 7");
        when(routeGeometryCache.getRouteColor("route-1")).thenReturn("#00ff00");
        when(terminalDepartureEtaService.enabled()).thenReturn(false);
        when(busStopRepository.findArrivingVehicles(any(), any(), any())).thenReturn(Flux.empty());
    }

    private VehiclePredictionState stateWithFixAt(Instant lastGpsUpdate) {
        return VehiclePredictionState.builder()
                .vehicleId("veh-1")
                .licensePlate("1234 AGA")
                .routeNumber("7")
                .routeId("route-1")
                .direction(0)
                .fractionOnRoute(0.2)
                .totalRouteDistanceMeters(10000)
                .speedKmh(30)
                .inMotion(true)
                .predictedLatitude(37.94)
                .predictedLongitude(58.40)
                .lastGpsUpdate(lastGpsUpdate)
                .build();
    }

    @Test
    void staleFixVehicleExcludedFromArrivals() {
        when(predictionService.getActiveStates())
                .thenReturn(List.of(stateWithFixAt(Instant.now().minusSeconds(300))));

        StepVerifier.create(service.findArrivingVehicles(stop)).verifyComplete();
    }

    @Test
    void freshFixVehicleKeptInArrivals() {
        when(predictionService.getActiveStates())
                .thenReturn(List.of(stateWithFixAt(Instant.now().minusSeconds(10))));

        StepVerifier.create(service.findArrivingVehicles(stop))
                .assertNext(row -> {
                    assertThat(row.getVehicleId()).isEqualTo("veh-1");
                    assertThat(row.getArrivalStatus()).isEqualTo("approaching");
                })
                .verifyComplete();
    }

    @Test
    void vehicleWithoutRealFixExcluded() {
        when(predictionService.getActiveStates())
                .thenReturn(List.of(stateWithFixAt(null)));

        StepVerifier.create(service.findArrivingVehicles(stop)).verifyComplete();
    }
}
