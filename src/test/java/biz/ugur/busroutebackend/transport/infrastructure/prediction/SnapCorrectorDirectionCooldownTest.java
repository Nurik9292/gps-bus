package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapCorrectorDirectionCooldownTest {

    private static final String ROUTE = "160";
    private static final String VEHICLE_ID = "veh-1";
    private static final String PLATE = "6368 AGJ";

    private static final double GPS_LAT = 37.90319;
    private static final double GPS_LON = 58.34163;
    private static final double PRIMARY_LAT = 37.90319;
    private static final double PRIMARY_LON = 58.34163;
    private static final double FLIPPED_LAT = 37.90325;
    private static final double FLIPPED_LON = 58.34160;

    @Mock
    private RouteGeometryCache routeGeometryCache;

    @Mock
    private MapMatchingService mapMatchingService;

    private PredictionProperties properties;
    private SnapCorrector snapCorrector;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        snapCorrector = new SnapCorrector(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ConsecutiveOppositeCounter(), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.OppositeFallbackStrategy(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ConsecutiveOppositeCounter()), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.HeadingFlipStrategy(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties)), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.FracFlipStrategy(properties, routeGeometryCache, mapMatchingService, new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.DirectionChangeCooldown(properties), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.PlausibilityChecker(properties)), new biz.ugur.busroutebackend.transport.infrastructure.prediction.snap.ImplausibleJumpHandler(), new biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer());

        List<double[]> backwardCoords = List.of(
                new double[]{37.90319, 58.34163},
                new double[]{37.90325, 58.34160});
        List<double[]> forwardCoords = List.of(
                new double[]{37.90319, 58.34163},
                new double[]{37.90325, 58.34160});

        lenient().when(routeGeometryCache.getPoints(eq(ROUTE), eq(0))).thenReturn(forwardCoords);
        lenient().when(routeGeometryCache.getPoints(eq(ROUTE), eq(1))).thenReturn(backwardCoords);
        lenient().when(routeGeometryCache.getTotalDistance(eq(ROUTE), anyInt())).thenReturn(10_000.0);
        lenient().when(routeGeometryCache.getCumulativeDistances(eq(ROUTE), anyInt())).thenReturn(null);

        MapMatchingService.SnappedResult primarySnap =
                new MapMatchingService.SnappedResult(PRIMARY_LAT, PRIMARY_LON, 0.86, 30.0, true);
        MapMatchingService.SnappedResult flippedSnap =
                new MapMatchingService.SnappedResult(FLIPPED_LAT, FLIPPED_LON, 0.14, 35.0, true);

        lenient().when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), any(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(primarySnap);
        lenient().when(mapMatchingService.snapToNearestSegment(anyDouble(), anyDouble(), any(), anyDouble()))
                .thenReturn(flippedSnap);

        lenient().when(mapMatchingService.calculateCourseFromRoute(any(), any(), anyDouble(), anyInt(), anyDouble()))
                .thenReturn(90.0);
    }

    private static VehiclePredictionState.VehiclePredictionStateBuilder backwardBaseline() {
        return VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(PLATE)
                .routeNumber(ROUTE)
                .direction(1)
                .predictedLatitude(37.90270)
                .predictedLongitude(58.34309)
                .fractionOnRoute(0.85)
                .lastGpsFraction(0.85)
                .inMotion(true)
                .rawGpsSpeedKmh(20.0);
    }

    @Test
    void blocksHeadingFlipDuringCooldownWithSoftEvidence() {
        VehiclePredictionState existing = backwardBaseline()
                .directionChangedAt(Instant.now().minusSeconds(2))
                .lastGpsFraction(-1)
                .build();

        double softOppositeCourse = 200.0;

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, softOppositeCourse, 1, java.time.Instant.now());

        assertThat(result.direction())
                .as("during cooldown a soft heading delta (<150deg) must NOT flip direction back to forward")
                .isEqualTo(1);
    }

    @Test
    void allowsHeadingFlipAfterCooldown() {
        VehiclePredictionState existing = backwardBaseline()
                .directionChangedAt(Instant.now().minusSeconds(6))
                .lastGpsFraction(-1)
                .build();

        double softOppositeCourse = 200.0;

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, softOppositeCourse, 1, java.time.Instant.now());

        assertThat(result.direction())
                .as("after cooldown expires, normal heading-flip behaviour resumes")
                .isEqualTo(0);
    }

    @Test
    void allowsHeadingFlipDuringCooldownWithHardEvidence() {
        VehiclePredictionState existing = backwardBaseline()
                .directionChangedAt(Instant.now().minusSeconds(2))
                .lastGpsFraction(-1)
                .build();

        double hardOppositeCourse = 270.0;

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, hardOppositeCourse, 1, java.time.Instant.now());

        assertThat(result.direction())
                .as("hard heading delta (>=150deg) overrides cooldown and flips direction")
                .isEqualTo(0);
    }

    @Test
    void blocksFracFlipDuringCooldownEvenWhenLastGpsFractionStillSet() {
        VehiclePredictionState existing = backwardBaseline()
                .directionChangedAt(Instant.now().minusSeconds(1))
                .lastGpsFraction(0.85)
                .build();

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, 90.0, 1, java.time.Instant.now());

        assertThat(result.direction())
                .as("during cooldown the frac-based DIR_CORRECT_FRAC must not flip direction")
                .isEqualTo(1);
    }

    @Test
    void normalFlipWithoutCooldown() {
        VehiclePredictionState existing = backwardBaseline()
                .directionChangedAt(null)
                .build();

        SnapCorrector.SnapResult result = snapCorrector.applySnap(
                existing, VEHICLE_ID, PLATE, ROUTE,
                GPS_LAT, GPS_LON, 270.0, 1, java.time.Instant.now());

        assertThat(result.direction())
                .as("without cooldown set, heading-flip behaviour is unchanged")
                .isEqualTo(0);
    }
}
