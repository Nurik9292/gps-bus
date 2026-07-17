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

class Case57TurnLagRegressionTest {

    private static final Instant PHYSICAL_TURN_ONTO_D1 = Instant.parse("2026-07-14T09:27:30Z");
    private static final Instant STARVED_SWITCH_HAPPENED_AT = Instant.parse("2026-07-14T09:36:04Z");
    private static final Instant TURN_MUST_BE_CONFIRMED_BY = Instant.parse("2026-07-14T09:33:00Z");

    @Test
    void movingTurnaroundOnRegularCadenceIsNotStarvedByGpsLostGate() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d1, d0);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        List<GpsFix> fixes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Case57TurnLagRegressionTest.class.getResourceAsStream(
                        "/fixtures/replay/case57-5424agj-20260714.jsonl"),
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
        assertThat(fixes).hasSizeGreaterThan(400);

        record DirChange(Instant at, int toDir) {
        }
        List<DirChange> changes = new ArrayList<>();
        Integer prevDir = null;
        for (GpsFix fx : fixes) {
            core.onFix(fx, topo);
            if (prevDir != null && core.direction() != prevDir) {
                changes.add(new DirChange(fx.timestamp(), core.direction()));
            }
            prevDir = core.direction();
        }

        Instant firstTurnOntoD1 = changes.stream()
                .filter(c -> c.toDir() == 1 && c.at().isAfter(PHYSICAL_TURN_ONTO_D1))
                .map(DirChange::at)
                .findFirst()
                .orElse(null);
        assertThat(firstTurnOntoD1)
                .as("разворот ходом на конце d0 засчитан")
                .isNotNull();
        assertThat(firstTurnOntoD1)
                .as("смена d0→d1 не голодает на штатном каденсе 20-40 c "
                        + "(GPS_LOST-гейт душил опрос банка до %s)", STARVED_SWITCH_HAPPENED_AT)
                .isBefore(TURN_MUST_BE_CONFIRMED_BY);

        long tripBoundaries = changes.size();
        assertThat(tripBoundaries)
                .as("количество смен направления за 2.5 часа — только честные развороты, без флапов")
                .isLessThanOrEqualTo(4);
    }
}
