package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapMatchingServiceTest {

    private MapMatchingService mapMatching;
    private PredictionProperties properties;

    private static final double START_LAT = 37.95;
    private static final double START_LON = 58.35;
    private static final double SEGMENT_LAT_STEP = 0.0009;
    private static final int POINT_COUNT = 200;

    @BeforeEach
    void setUp() {
        properties = new PredictionProperties();
        mapMatching = new MapMatchingService(properties);
    }

    @Test
    void windowedSnap_matchesGlobalSnap_whenPointInsideWindow() {
        List<double[]> route = buildSyntheticRoute();
        double[] cumDist = computeCumulativeDistances(route);
        double totalDist = cumDist[cumDist.length - 1];

        int targetSegment = 100;
        double[] gpsPoint = pointNearSegment(route, targetSegment, 0.0001);
        double currentFraction = (double) targetSegment / (POINT_COUNT - 1);

        MapMatchingService.SnappedResult global = mapMatching
                .snapToNearestSegment(gpsPoint[0], gpsPoint[1], route, totalDist);
        MapMatchingService.SnappedResult windowed = mapMatching
                .snapToNearestSegment(gpsPoint[0], gpsPoint[1], route, cumDist, totalDist,
                        currentFraction, 0.20);

        assertThat(windowed.snapped()).isTrue();
        assertThat(global.snapped()).isTrue();
        assertThat(windowed.fraction()).isCloseTo(global.fraction(), org.assertj.core.data.Offset.offset(0.0001));
        assertThat(windowed.latitude()).isCloseTo(global.latitude(), org.assertj.core.data.Offset.offset(1e-7));
        assertThat(windowed.longitude()).isCloseTo(global.longitude(), org.assertj.core.data.Offset.offset(1e-7));
    }

    @Test
    void windowedSnap_fallsBackToGlobal_whenPointOutsideWindow() {
        List<double[]> route = buildSyntheticRoute();
        double[] cumDist = computeCumulativeDistances(route);
        double totalDist = cumDist[cumDist.length - 1];

        int targetSegment = 180;
        double[] gpsPoint = pointNearSegment(route, targetSegment, 0.0001);
        double currentFraction = 0.05;
        double smallWindow = 0.02;

        MapMatchingService.SnappedResult result = mapMatching
                .snapToNearestSegment(gpsPoint[0], gpsPoint[1], route, cumDist, totalDist,
                        currentFraction, smallWindow);

        assertThat(result.snapped()).isTrue();
        double expectedFraction = (double) targetSegment / (POINT_COUNT - 1);
        assertThat(result.fraction()).isCloseTo(expectedFraction, org.assertj.core.data.Offset.offset(0.005));
    }

    @Test
    void calculateCourseFromRoute_binarySearchMatchesLinear() {
        List<double[]> route = buildSyntheticRoute();
        double[] cumDist = computeCumulativeDistances(route);
        double totalDist = cumDist[cumDist.length - 1];

        for (double fraction : new double[]{0.05, 0.25, 0.5, 0.75, 0.95}) {
            double withCum = mapMatching.calculateCourseFromRoute(route, cumDist, fraction, 0, totalDist);
            double linear = mapMatching.calculateCourseFromRoute(route, null, fraction, 0, totalDist);
            assertThat(withCum)
                    .as("course at fraction %.2f", fraction)
                    .isCloseTo(linear, org.assertj.core.data.Offset.offset(0.01));
        }
    }

    @Test
    void interpolateRoutePoint_binarySearchMatchesLinear() {
        List<double[]> route = buildSyntheticRoute();
        double[] cumDist = computeCumulativeDistances(route);
        double totalDist = cumDist[cumDist.length - 1];

        for (double fraction : new double[]{0.10, 0.33, 0.66, 0.90}) {
            double[] withCum = mapMatching.interpolateRoutePoint(route, cumDist, fraction, totalDist);
            double[] linear = mapMatching.interpolateRoutePoint(route, null, fraction, totalDist);
            assertThat(withCum[0]).isCloseTo(linear[0], org.assertj.core.data.Offset.offset(1e-9));
            assertThat(withCum[1]).isCloseTo(linear[1], org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Test
    void windowedSnap_fallsBackWhenCumDistLengthMismatch() {
        List<double[]> route = buildSyntheticRoute();
        double totalDist = computeCumulativeDistances(route)[POINT_COUNT - 1];
        double[] mismatchedCumDist = new double[POINT_COUNT - 5];

        double[] gpsPoint = pointNearSegment(route, 50, 0.0001);
        MapMatchingService.SnappedResult result = mapMatching
                .snapToNearestSegment(gpsPoint[0], gpsPoint[1], route, mismatchedCumDist, totalDist,
                        0.25, 0.20);

        assertThat(result.snapped()).isTrue();
        double expectedFraction = 50.0 / (POINT_COUNT - 1);
        assertThat(result.fraction()).isCloseTo(expectedFraction, org.assertj.core.data.Offset.offset(0.005));
    }

    private List<double[]> buildSyntheticRoute() {
        List<double[]> route = new ArrayList<>(POINT_COUNT);
        for (int i = 0; i < POINT_COUNT; i++) {
            double lat = START_LAT + i * SEGMENT_LAT_STEP;
            route.add(new double[]{lat, START_LON});
        }
        return route;
    }

    private double[] computeCumulativeDistances(List<double[]> route) {
        double[] cumDist = new double[route.size()];
        cumDist[0] = 0;
        for (int i = 1; i < route.size(); i++) {
            cumDist[i] = cumDist[i - 1] + DistanceCalculationService.haversineDistanceMeters(
                    route.get(i - 1)[0], route.get(i - 1)[1],
                    route.get(i)[0], route.get(i)[1]);
        }
        return cumDist;
    }

    private double[] pointNearSegment(List<double[]> route, int segmentIndex, double offset) {
        double[] a = route.get(segmentIndex);
        double[] b = route.get(segmentIndex + 1);
        double midLat = (a[0] + b[0]) / 2.0;
        double midLon = (a[1] + b[1]) / 2.0;
        return new double[]{midLat, midLon + offset};
    }
}
