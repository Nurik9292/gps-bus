package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.domain.repository.StopDwellStatsRepository;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehiclePositionPredictorLeashTest {

    @Mock
    private RouteGeometryCache routeGeometryCache;
    @Mock
    private MapMatchingService mapMatchingService;
    @Mock
    private StopDwellStatsRepository dwellStatsRepository;
    @Mock
    private SegmentTravelStatsRepository segmentTravelStatsRepository;
    @Mock
    private PipelineTracer pipelineTracer;

    private PredictionProperties properties;
    private VehiclePositionPredictor predictor;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        predictor = new VehiclePositionPredictor(properties, routeGeometryCache,
                mapMatchingService, dwellStatsRepository, segmentTravelStatsRepository,
                pipelineTracer);
        lenient().when(routeGeometryCache.getPoints(anyString(), anyInt())).thenReturn(null);
    }

    private VehiclePredictionState movingStateAt(double lat, double lon) {
        return VehiclePredictionState.builder()
                .vehicleId("veh-1935")
                .licensePlate("1935 AGJ")
                .routeNumber("13")
                .routeId("route-legacy-14")
                .direction(0)
                .fractionOnRoute(0.5)
                .lastGpsFraction(0.5)
                .predictedLatitude(lat)
                .predictedLongitude(lon)
                .gpsLatitude(lat)
                .gpsLongitude(lon)
                .speedKmh(40.0)
                .smoothedSpeedKmh(40.0)
                .rawGpsSpeedKmh(40.0)
                .longTermAvgSpeedKmh(30.0)
                .course(90.0)
                .inMotion(true)
                .lastReceivedAt(Instant.now())
                .lastGpsUpdate(Instant.now())
                .build();
    }

    @Test
    void silentGpsCannotDragMarkerBeyondLeash() {
        VehiclePredictionState state = movingStateAt(37.957748, 58.355170);

        for (int tick = 0; tick < 300; tick++) {
            state = predictor.advance(state);
        }

        double driftMeters = DistanceCalculationService.haversineDistanceMeters(
                state.getPredictedLatitude(), state.getPredictedLongitude(),
                state.getGpsLatitude(), state.getGpsLongitude());
        assertThat(driftMeters)
                .as("прогноз без свежего GPS не должен утаскивать маркер дальше поводка")
                .isLessThanOrEqualTo(properties.getMaxAdvanceLeashMeters() + 20.0);
    }

    @Test
    void disabledLeashKeepsLegacyRunawayBehaviour() {
        properties.setMaxAdvanceLeashMeters(0.0);
        VehiclePredictionState state = movingStateAt(37.957748, 58.355170);

        for (int tick = 0; tick < 300; tick++) {
            state = predictor.advance(state);
        }

        double driftMeters = DistanceCalculationService.haversineDistanceMeters(
                state.getPredictedLatitude(), state.getPredictedLongitude(),
                state.getGpsLatitude(), state.getGpsLongitude());
        assertThat(driftMeters).isGreaterThan(1000.0);
    }
}
