package biz.ugur.busroutebackend.routing.domain.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WalkingGeometryGuardTest {

    @Test
    void plausibleWalkIsNotFlagged() {
        assertThat(WalkingGeometryGuard.isImplausibleDetour(400.0, 480)).isFalse();
    }

    @Test
    void largeProportionalAndAbsoluteDetourIsFlagged() {
        assertThat(WalkingGeometryGuard.isImplausibleDetour(400.0, 1000)).isTrue();
    }

    @Test
    void shortLegitimateDetourIsNotFlagged() {
        assertThat(WalkingGeometryGuard.isImplausibleDetour(30.0, 90)).isFalse();
    }

    @Test
    void zeroStraightLineCannotBeJudged() {
        assertThat(WalkingGeometryGuard.isImplausibleDetour(0.0, 500)).isFalse();
    }

    @Test
    void detourExactlyAtRatioThresholdIsNotFlagged() {
        assertThat(WalkingGeometryGuard.isImplausibleDetour(400.0, 800)).isFalse();
    }

    @Test
    void detourJustOverRatioWithLargeExcessIsFlagged() {
        assertThat(WalkingGeometryGuard.isImplausibleDetour(400.0, 801)).isTrue();
    }
}
