package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PredictionPropertiesTest {

    @Test
    void ghostBroadcastWindowConsistentWhenStopAdvanceNotBeforeMaxAgeByDefault() {
        PredictionProperties props = new PredictionProperties();

        assertThat(props.isGhostBroadcastWindowConsistent()).isTrue();
    }

    @Test
    void ghostBroadcastWindowInconsistentWhenAdvanceStopsBeforeBroadcastGate() {
        PredictionProperties props = new PredictionProperties();
        props.setMaxAgeMs(90_000);
        props.setStopAdvanceAfterMs(30_000);

        assertThat(props.isGhostBroadcastWindowConsistent()).isFalse();
    }

    @Test
    void ghostBroadcastWindowConsistentWhenAdvanceOutlastsBroadcastGate() {
        PredictionProperties props = new PredictionProperties();
        props.setMaxAgeMs(90_000);
        props.setStopAdvanceAfterMs(120_000);

        assertThat(props.isGhostBroadcastWindowConsistent()).isTrue();
    }
}
