package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VehiclePositionPredictionServiceTrajectoryTest {

    private static final double ASHGABAT_LAT = 37.94;
    private static final double ASHGABAT_LON = 58.38;

    private static double latAhead(double baseLat, double metersNorth) {
        return baseLat + metersNorth / 111_320.0;
    }

    @Test
    void rejectsWhenPendingFractionUnknown() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                -1.0, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.5, 0, ASHGABAT_LAT, ASHGABAT_LON
        )).isFalse();
    }

    @Test
    void rejectsWhenNewFractionUnknown() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.5, 0, ASHGABAT_LAT, ASHGABAT_LON,
                -1.0, 0, ASHGABAT_LAT, ASHGABAT_LON
        )).isFalse();
    }

    @Test
    void rejectsWhenDirectionDiffers() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.50, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.51, 1, latAhead(ASHGABAT_LAT, 50), ASHGABAT_LON
        )).isFalse();
    }

    @Test
    void rejectsWhenFractionDidNotAdvance() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.50, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.50, 0, latAhead(ASHGABAT_LAT, 50), ASHGABAT_LON
        )).isFalse();
    }

    @Test
    void rejectsWhenFractionWentBackwards() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.55, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.50, 0, latAhead(ASHGABAT_LAT, 50), ASHGABAT_LON
        )).isFalse();
    }

    @Test
    void rejectsWhenFractionJumpExceedsTenPercentOfRoute() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.20, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.35, 0, latAhead(ASHGABAT_LAT, 100), ASHGABAT_LON
        )).isFalse();
    }

    @Test
    void rejectsWhenPhysicalStepExceedsMaxStep() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.50, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.51, 0, latAhead(ASHGABAT_LAT, 800), ASHGABAT_LON
        )).isFalse();
    }

    @Test
    void acceptsSmallForwardAdvanceWithinPhysicalLimits() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.500, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.503, 0, latAhead(ASHGABAT_LAT, 120), ASHGABAT_LON
        )).isTrue();
    }

    @Test
    void acceptsForwardAdvanceForBackwardDirection() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.300, 1, ASHGABAT_LAT, ASHGABAT_LON,
                0.305, 1, latAhead(ASHGABAT_LAT, 80), ASHGABAT_LON
        )).isTrue();
    }

    @Test
    void acceptsAdvanceAtEdgeOfFracDeltaTolerance() {
        assertThat(VehiclePositionPredictionService.isTrajectoryAdvance(
                0.500, 0, ASHGABAT_LAT, ASHGABAT_LON,
                0.599, 0, latAhead(ASHGABAT_LAT, 400), ASHGABAT_LON
        )).isTrue();
    }
}
