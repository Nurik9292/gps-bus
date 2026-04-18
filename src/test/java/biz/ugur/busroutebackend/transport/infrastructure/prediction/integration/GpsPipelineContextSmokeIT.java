package biz.ugur.busroutebackend.transport.infrastructure.prediction.integration;

import biz.ugur.busroutebackend.shared.test.integration.GpsPipelineIntegrationTestBase;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class GpsPipelineContextSmokeIT extends GpsPipelineIntegrationTestBase {

    @Autowired
    private VehiclePositionPredictionService predictionService;

    @Test
    void applicationContextLoadsWithPostgisAndRedis() {
        assertThat(predictionService).isNotNull();
        assertThat(predictionService.getActiveStateCount()).isZero();
    }
}
