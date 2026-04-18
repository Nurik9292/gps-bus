package biz.ugur.busroutebackend.transport.infrastructure.prediction.integration;

import biz.ugur.busroutebackend.shared.test.integration.GpsPipelineIntegrationTestBase;
import biz.ugur.busroutebackend.shared.test.integration.GpsPipelineReplayRunner;
import biz.ugur.busroutebackend.shared.test.integration.PredictionSnapshot;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GpsPipelineReplayIT extends GpsPipelineIntegrationTestBase {

    @Autowired
    private VehiclePositionPredictionService predictionService;

    @Test
    void syntheticSmokeFixtureProducesDeterministicSnapshots() throws Exception {
        GpsPipelineReplayRunner runner = new GpsPipelineReplayRunner(predictionService);

        List<List<PredictionSnapshot>> snapshots = runner.replay("synthetic-smoke");

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(2))
                .anySatisfy(snap -> assertThat(snap.vehicleId()).isEqualTo("test-vehicle-1"));
    }
}
