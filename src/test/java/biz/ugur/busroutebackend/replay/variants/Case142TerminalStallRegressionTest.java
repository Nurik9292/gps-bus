package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Case142TerminalStallRegressionTest {

    private static final Instant STAND_STALL_ENTERED = Instant.parse("2026-07-15T07:55:11Z");
    private static final Instant STAND_STILL_STUCK_AT = Instant.parse("2026-07-15T08:25:00Z");

    @Test
    void liveTurnaroundOnRoute142UnsticksWithinMinutesInsteadOfHalfHour() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-142-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-142-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d0, d1);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        List<GpsFix> fixes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Case142TerminalStallRegressionTest.class.getResourceAsStream(
                        "/fixtures/replay/case142-1243agj-20260715.jsonl"),
                StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.isBlank()) continue;
                JsonNode j = mapper.readTree(ln);
                fixes.add(new GpsFix(j.get("vehicleId").asText(),
                        j.path("licensePlate").asText(""), "142",
                        j.get("latitude").asDouble(), j.get("longitude").asDouble(),
                        j.path("speedKmh").asDouble(0), j.path("course").asDouble(0),
                        j.path("inMotion").asBoolean(false),
                        Instant.parse(j.get("timestamp").asText()),
                        j.path("direction").asInt(0), null, null, null,
                        Instant.parse(j.get("timestamp").asText())));
            }
        }
        assertThat(fixes).hasSizeGreaterThan(100);

        Instant unstuckAt = null;
        for (GpsFix fx : fixes) {
            core.onFix(fx, topo);
            if (unstuckAt == null && core.tripId() >= 2
                    && core.bank().leader().variantId().endsWith("#d1")) {
                unstuckAt = fx.timestamp();
            }
        }

        assertThat(unstuckAt)
                .as("реальный разворот 1243 AGJ 15.07: банк переключается на d1 (на стенде висел 30+ мин)")
                .isNotNull()
                .isBefore(STAND_STILL_STUCK_AT);

        long minutesAfterStall = java.time.Duration.between(STAND_STALL_ENTERED, unstuckAt).toMinutes();
        assertThat(minutesAfterStall)
                .as("разлипание в первые минуты после разворота, не через полчаса")
                .isLessThanOrEqualTo(6);
    }
}
