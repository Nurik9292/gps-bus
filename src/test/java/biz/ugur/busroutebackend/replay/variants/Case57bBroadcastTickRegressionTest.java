package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
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

class Case57bBroadcastTickRegressionTest {

    private static final Instant BUS_LONG_ON_D1_FROM = Instant.parse("2026-07-17T12:36:00Z");

    @Test
    void oneHertzBroadcastTickerDoesNotStarveBankProgress() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d0, d1);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        List<GpsFix> fixes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Case57bBroadcastTickRegressionTest.class.getResourceAsStream(
                        "/fixtures/replay/case57b-5898agj-20260717.jsonl"),
                StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.isBlank()) continue;
                JsonNode j = mapper.readTree(ln);
                fixes.add(new GpsFix(j.get("vehicleId").asText(),
                        j.path("licensePlate").asText(""), "57",
                        j.get("latitude").asDouble(), j.get("longitude").asDouble(),
                        j.path("speedKmh").asDouble(0), j.path("course").asDouble(0),
                        j.path("inMotion").asBoolean(false),
                        Instant.parse(j.get("timestamp").asText()),
                        j.path("direction").asInt(0), null, null, null,
                        Instant.parse(j.get("timestamp").asText())));
            }
        }
        assertThat(fixes).hasSizeGreaterThan(200);

        int terminalStallTicksInTail = 0;
        int maxLeaderProgressStreakInTail = 0;
        Instant prevTs = null;
        for (GpsFix fx : fixes) {
            if (prevTs != null) {
                Instant t = prevTs.plusSeconds(1);
                while (!t.isAfter(fx.timestamp())) {
                    core.broadcastTick(t);
                    t = t.plusSeconds(1);
                }
            }
            prevTs = fx.timestamp();
            var est = core.onFix(fx, topo);
            if (!fx.timestamp().isBefore(BUS_LONG_ON_D1_FROM)) {
                if ("AT_TERMINAL".equals(est.mode()) && core.direction() == 0) {
                    terminalStallTicksInTail++;
                }
                maxLeaderProgressStreakInTail = Math.max(maxLeaderProgressStreakInTail,
                        core.bank().leader().progressStreak());
            }
        }

        assertThat(core.direction())
                .as("после выезда с конечной ведение по d1 (жило: AT_TERMINAL d0 при борте в 1.5+ км)")
                .isEqualTo(1);
        assertThat(terminalStallTicksInTail)
                .as("хвост эпизода не залипает в AT_TERMINAL d0")
                .isZero();
        assertThat(maxLeaderProgressStreakInTail)
                .as("прогресс банка жив при 1 Гц вещательном тикере "
                        + "(broadcastTick сжимал dTau банка до ~1 c — прогресс никогда не рос)")
                .isGreaterThanOrEqualTo(2);
    }
}
