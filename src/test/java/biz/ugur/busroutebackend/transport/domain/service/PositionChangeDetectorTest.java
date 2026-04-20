package biz.ugur.busroutebackend.transport.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PositionChangeDetectorTest {

    private PositionChangeDetector detector;

    @BeforeEach
    void setUp() {
        detector = new PositionChangeDetector();
    }

    @Nested
    class SignificantChange {

        @Test
        void firstPositionIsAlwaysSignificant() {
            assertTrue(detector.hasSignificantChange(null, null, 0.0,
                    37.9601, 58.3261, 10.0));
            assertTrue(detector.hasSignificantChange(null, 58.0, null,
                    37.96, 58.32, 5.0));
        }

        @Test
        void smallPositionDeltaAndStableSpeedIsNotSignificant() {
            assertFalse(detector.hasSignificantChange(
                    37.96000, 58.32000, 10.0,
                    37.96001, 58.32001, 10.0));
        }

        @Test
        void meaningfulPositionDeltaIsSignificant() {
            assertTrue(detector.hasSignificantChange(
                    37.9600, 58.3200, 10.0,
                    37.9650, 58.3250, 10.0));
        }

        @Test
        void meaningfulSpeedDeltaIsSignificantEvenWhenPositionStays() {
            assertTrue(detector.hasSignificantChange(
                    37.9601, 58.3261, 5.0,
                    37.9601, 58.3261, 20.0));
        }
    }

    @Nested
    class PositionChange {

        @Test
        void nullCoordinatesNotSignificant() {
            assertFalse(detector.hasSignificantPositionChange(null, 58.0, 37.96, 58.32));
            assertFalse(detector.hasSignificantPositionChange(37.0, null, 37.96, 58.32));
            assertFalse(detector.hasSignificantPositionChange(37.0, 58.0, null, 58.32));
            assertFalse(detector.hasSignificantPositionChange(37.0, 58.0, 37.96, null));
        }

        @Test
        void belowThresholdReturnsFalse() {
            assertFalse(detector.hasSignificantPositionChange(37.96001, 58.32001, 37.96001, 58.32001));
        }

        @Test
        void aboveThresholdLatReturnsTrue() {
            assertTrue(detector.hasSignificantPositionChange(37.9600, 58.32, 37.9700, 58.32));
        }

        @Test
        void aboveThresholdLonReturnsTrue() {
            assertTrue(detector.hasSignificantPositionChange(37.96, 58.3200, 37.96, 58.3300));
        }
    }

    @Nested
    class SpeedChange {

        @Test
        void nullSpeedNotSignificant() {
            assertFalse(detector.hasSignificantSpeedChange(null, 10.0));
            assertFalse(detector.hasSignificantSpeedChange(10.0, null));
            assertFalse(detector.hasSignificantSpeedChange(null, null));
        }

        @Test
        void smallSpeedDeltaNotSignificant() {
            assertFalse(detector.hasSignificantSpeedChange(10.0, 11.0));
        }

        @Test
        void largeSpeedDeltaSignificant() {
            assertTrue(detector.hasSignificantSpeedChange(10.0, 30.0));
            assertTrue(detector.hasSignificantSpeedChange(30.0, 10.0));
        }
    }

    @Nested
    class Motion {

        @Test
        void belowThresholdIsNotInMotion() {
            assertFalse(detector.isInMotion(1.0));
            assertFalse(detector.isInMotion(2.9));
        }

        @Test
        void atOrAboveThresholdIsInMotion() {
            assertTrue(detector.isInMotion(3.0));
            assertTrue(detector.isInMotion(20.0));
        }

        @Test
        void nullSpeedIsNotInMotion() {
            assertFalse(detector.isInMotion(null));
        }
    }

    @Test
    void thresholdsExposeCurrentValues() {
        PositionChangeDetector.Thresholds t = detector.getThresholds();
        assertEquals(0.00005, t.positionDeltaThreshold(), 1e-12);
        assertEquals(2.0, t.speedDeltaThresholdKmh());
        assertEquals(3.0, t.minSpeedForMotionKmh());
        assertTrue(t.positionDeltaMeters() > 0);
        assertEquals(0.00005 * 111_000, t.positionDeltaMeters(), 1e-9);
    }

    @Test
    void customConstructorHonouredByThresholds() {
        PositionChangeDetector custom = new PositionChangeDetector(0.001, 5.0, 1.0);
        PositionChangeDetector.Thresholds t = custom.getThresholds();
        assertEquals(0.001, t.positionDeltaThreshold());
        assertEquals(5.0, t.speedDeltaThresholdKmh());
        assertEquals(1.0, t.minSpeedForMotionKmh());
    }
}
