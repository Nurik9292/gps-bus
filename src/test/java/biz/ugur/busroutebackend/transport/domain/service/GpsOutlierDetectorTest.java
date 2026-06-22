package biz.ugur.busroutebackend.transport.domain.service;

import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult;
import biz.ugur.busroutebackend.transport.domain.valueobject.OutlierDetectionResult.OutlierType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GpsOutlierDetectorTest {

    private static final String DEVICE = "d-1";

    private GpsOutlierDetector detector;

    @BeforeEach
    void setUp() {
        detector = new GpsOutlierDetector();
    }

    private static LocalDateTime t(int secondsOffset) {
        return LocalDateTime.of(2026, 5, 1, 10, 0).plusSeconds(secondsOffset);
    }

    @Nested
    class PointPair {

        @Test
        void disabledDetectorAlwaysReturnsDisabled() {
            GpsOutlierDetector off = new GpsOutlierDetector(false, 150.0, 3, 60, 10.0);
            OutlierDetectionResult r = off.detect(DEVICE,
                    37.96, 58.32, t(10),
                    37.97, 58.33, t(0));
            assertEquals(OutlierType.DETECTION_DISABLED, r.type());
            assertFalse(r.isOutlier());
        }

        @Test
        void noHistoryWhenPreviousTimestampIsNull() {
            OutlierDetectionResult r = detector.detect(DEVICE,
                    37.96, 58.32, t(10),
                    37.97, 58.33, null);
            assertEquals(OutlierType.NO_HISTORY, r.type());
        }

        @Test
        void timeGapTooSmallBelowMinimum() {
            OutlierDetectionResult r = detector.detect(DEVICE,
                    37.96, 58.32, t(1),
                    37.97, 58.33, t(0));
            assertEquals(OutlierType.TIME_GAP_TOO_SMALL, r.type());
            assertFalse(r.isOutlier());
        }

        @Test
        void largeGapWithPlausibleMoveIsValid() {
            OutlierDetectionResult r = detector.detect(DEVICE,
                    37.96, 58.32, t(120),
                    37.97, 58.33, t(0));
            assertEquals(OutlierType.VALID, r.type());
            assertFalse(r.isOutlier());
        }

        @Test
        void distanceTooSmallBelowMinimum() {
            OutlierDetectionResult r = detector.detect(DEVICE,
                    37.9601, 58.3261, t(10),
                    37.9601, 58.3261, t(0));
            assertEquals(OutlierType.DISTANCE_TOO_SMALL, r.type());
        }

        @Test
        void validPositionWithin150kmh() {
            OutlierDetectionResult r = detector.detect(DEVICE,
                    37.9610, 58.3270, t(30),
                    37.9601, 58.3261, t(0));
            assertEquals(OutlierType.VALID, r.type());
            assertFalse(r.isOutlier());
            assertTrue(r.impliedSpeedKmh() > 0);
            assertTrue(r.wasEvaluated());
        }

        @Test
        void outlierWhenImpliedSpeedExceedsMax() {
            OutlierDetectionResult r = detector.detect(DEVICE,
                    40.0, 60.0, t(10),
                    37.96, 58.32, t(0));
            assertEquals(OutlierType.SPEED_EXCEEDED, r.type());
            assertTrue(r.isOutlier());
            assertTrue(r.impliedSpeedKmh() > 150.0);
        }

        @Test
        void largeGapWithImpossibleSpeedIsOutlier() {
            OutlierDetectionResult r = detector.detect(DEVICE,
                    40.0, 60.0, t(120),
                    37.96, 58.32, t(0));
            assertEquals(OutlierType.SPEED_EXCEEDED, r.type());
            assertTrue(r.isOutlier());
            assertTrue(r.impliedSpeedKmh() > 150.0);
        }
    }

    @Nested
    class History {

        @Test
        void noHistoryResultWhenListEmpty() {
            OutlierDetectionResult r = detector.detectWithHistory(DEVICE,
                    37.96, 58.32, t(0), List.of());
            assertEquals(OutlierType.NO_HISTORY, r.type());
        }

        @Test
        void noHistoryResultWhenListNull() {
            OutlierDetectionResult r = detector.detectWithHistory(DEVICE,
                    37.96, 58.32, t(0), null);
            assertEquals(OutlierType.NO_HISTORY, r.type());
        }

        @Test
        void usesMostRecentPointForPairwiseDetection() {
            GpsHistory mostRecent = new GpsHistory(37.9601, 58.3261, t(0));
            OutlierDetectionResult r = detector.detectWithHistory(DEVICE,
                    37.9610, 58.3270, t(30),
                    List.of(mostRecent));
            assertEquals(OutlierType.VALID, r.type());
        }

        @Test
        void frozenCoordinatesWithMotionWhenReportedSpeedHigh() {
            GpsHistory frozen = new GpsHistory(37.9601, 58.3261, t(0));
            OutlierDetectionResult r = detector.detectWithHistory(DEVICE,
                    37.9601, 58.3261, t(30),
                    List.of(frozen),
                    30.0);
            assertEquals(OutlierType.FROZEN_COORDINATES_WITH_MOTION, r.type());
            assertTrue(r.isOutlier());
        }

        @Test
        void frozenDetectionSkippedWhenReportedSpeedBelowThreshold() {
            GpsHistory frozen = new GpsHistory(37.9601, 58.3261, t(0));
            OutlierDetectionResult r = detector.detectWithHistory(DEVICE,
                    37.9601, 58.3261, t(30),
                    List.of(frozen),
                    2.0);
            assertEquals(OutlierType.DISTANCE_TOO_SMALL, r.type());
        }

        @Test
        void disabledDetectorShortCircuitsHistoryPath() {
            GpsOutlierDetector off = new GpsOutlierDetector(false, 150.0, 3, 60, 10.0);
            OutlierDetectionResult r = off.detectWithHistory(DEVICE,
                    37.96, 58.32, t(0),
                    List.of(new GpsHistory(37.97, 58.33, t(-10))));
            assertEquals(OutlierType.DETECTION_DISABLED, r.type());
        }
    }

    @Test
    void configurationExposesCurrentSettings() {
        GpsOutlierDetector.Configuration cfg = detector.getConfiguration();
        assertTrue(cfg.enabled());
        assertEquals(150.0, cfg.maxImpliedSpeedKmh());
        assertEquals(3, cfg.minTimeDifferenceSeconds());
        assertEquals(60, cfg.maxTimeDifferenceSeconds());
        assertEquals(10.0, cfg.minDistanceMeters());
    }

    @Test
    void resultFactoriesProduceConsistentTypes() {
        assertEquals(OutlierType.VALID,
                OutlierDetectionResult.valid(DEVICE, 50.0, 100.0, 10, 150.0).type());
        assertEquals(OutlierType.SPEED_EXCEEDED,
                OutlierDetectionResult.outlier(DEVICE, 200.0, 1000.0, 10, 150.0).type());
        assertEquals(OutlierType.DETECTION_DISABLED,
                OutlierDetectionResult.disabled(DEVICE).type());
        assertTrue(OutlierDetectionResult.outlier(DEVICE, 200.0, 1000.0, 10, 150.0).isOutlier());
    }

    @Test
    void resultDescriptionsAreInformative() {
        for (OutlierType type : OutlierType.values()) {
            OutlierDetectionResult r;
            switch (type) {
                case VALID -> r = OutlierDetectionResult.valid(DEVICE, 50.0, 100.0, 10, 150.0);
                case SPEED_EXCEEDED -> r = OutlierDetectionResult.outlier(DEVICE, 200.0, 1000.0, 10, 150.0);
                case FROZEN_COORDINATES_WITH_MOTION ->
                        r = OutlierDetectionResult.frozenCoordinatesWithMotion(DEVICE, 1.0, 30.0);
                case NO_HISTORY -> r = OutlierDetectionResult.noHistory(DEVICE, 150.0);
                case TIME_GAP_TOO_LARGE -> r = OutlierDetectionResult.timeGapTooLarge(DEVICE, 120, 150.0);
                case TIME_GAP_TOO_SMALL -> r = OutlierDetectionResult.timeGapTooSmall(DEVICE, 1, 150.0);
                case DISTANCE_TOO_SMALL -> r = OutlierDetectionResult.distanceTooSmall(DEVICE, 3.0, 150.0);
                case DETECTION_DISABLED -> r = OutlierDetectionResult.disabled(DEVICE);
                default -> throw new IllegalStateException();
            }
            assertNotNull(r.getDescription());
            assertFalse(r.getDescription().isBlank(), type.name());
        }
    }

    private static class GpsHistory implements GpsOutlierDetector.GpsHistoryPoint {
        private final double lat, lon;
        private final LocalDateTime ts;

        GpsHistory(double lat, double lon, LocalDateTime ts) {
            this.lat = lat;
            this.lon = lon;
            this.ts = ts;
        }

        public double getLatitude() { return lat; }
        public double getLongitude() { return lon; }
        public LocalDateTime getTimestamp() { return ts; }
    }
}
