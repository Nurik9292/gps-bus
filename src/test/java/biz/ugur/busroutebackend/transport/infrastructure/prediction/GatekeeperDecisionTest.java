package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GatekeeperDecisionTest {

    @Test
    void allowsCoordinateWrite_forAcceptAndForceAcceptStale() {
        assertThat(GatekeeperDecision.ACCEPT.allowsCoordinateWrite()).isTrue();
        assertThat(GatekeeperDecision.FORCE_ACCEPT_STALE.allowsCoordinateWrite()).isTrue();
    }

    @Test
    void disallowsCoordinateWrite_forRejectAndPendingAndColdStart() {
        assertThat(GatekeeperDecision.REJECT_OUTLIER.allowsCoordinateWrite()).isFalse();
        assertThat(GatekeeperDecision.REJECT_TELEPORT_GAP.allowsCoordinateWrite()).isFalse();
        assertThat(GatekeeperDecision.REJECT_OFF_ROUTE.allowsCoordinateWrite()).isFalse();
        assertThat(GatekeeperDecision.PENDING_TELEPORT.allowsCoordinateWrite()).isFalse();
        assertThat(GatekeeperDecision.COLD_START.allowsCoordinateWrite()).isFalse();
    }
}
