package biz.ugur.busroutebackend;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Needs full environment (Postgres, Redis, JWT/admin env vars). " +
        "Run with: ./mvnw verify -Dit.test=GpsPipelineContextSmokeIT or via Testcontainers-based IT. " +
        "Tracked in docs/plans/TEST_INFRASTRUCTURE_TODO.md.")
class BusRouteBackendApplicationTests {

    @Test
    void contextLoads() {
    }

}
