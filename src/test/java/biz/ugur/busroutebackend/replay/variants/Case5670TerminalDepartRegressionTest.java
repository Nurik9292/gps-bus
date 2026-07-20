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

class Case5670TerminalDepartRegressionTest {

    private static final Instant PHYSICAL_DEPART = Instant.parse("2026-07-14T10:39:36Z");
    private static final Instant SWITCH_DEADLINE = Instant.parse("2026-07-14T10:41:40Z");

    @Test
    void turnChannelSurvivesCityCadenceAndSwitchesWithinTwoMinutesOfDeparture() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d0, d1);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        List<GpsFix> fixes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Case5670TerminalDepartRegressionTest.class.getResourceAsStream(
                        "/fixtures/replay/case5670-5670agj-20260714.jsonl"),
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
        assertThat(fixes).hasSizeGreaterThan(300);

        Instant prevTs = null;
        Integer prevDir = null;
        Instant switchToD0At = null;
        int directionChanges = 0;
        for (GpsFix fx : fixes) {
            if (prevTs != null) {
                Instant t = prevTs.plusSeconds(1);
                while (!t.isAfter(fx.timestamp())) {
                    core.broadcastTick(t);
                    t = t.plusSeconds(1);
                }
            }
            prevTs = fx.timestamp();
            core.onFix(fx, topo);
            int dir = core.direction();
            if (prevDir != null && dir != prevDir) {
                directionChanges++;
                if (dir == 0 && switchToD0At == null
                        && !fx.timestamp().isBefore(PHYSICAL_DEPART)) {
                    switchToD0At = fx.timestamp();
                }
            }
            prevDir = dir;
        }

        assertThat(switchToD0At)
                .as("смена d1→d0 после выезда с конечной обязана произойти")
                .isNotNull();
        assertThat(switchToD0At)
                .as("канал разворота не должен умирать от GPS_LOST на каденсе 20 c "
                        + "(жило: смена только в 10:45:36 банком, лаг 360 c)")
                .isBeforeOrEqualTo(SWITCH_DEADLINE);
        assertThat(directionChanges)
                .as("анти-флап: за окно 09:40-11:30 не больше трёх смен направления "
                        + "(d0-инициализация→d1, d1→d0 разворот, возможная концевая)")
                .isLessThanOrEqualTo(3);
    }
}
