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

class Case160GurtlyTurnLagRegressionTest {

    private static final Instant PHYSICAL_TURN_STARTED = Instant.parse("2026-07-14T11:15:00Z");
    private static final Instant LIVE_MODEL_STILL_BACKWARDS_UNTIL = Instant.parse("2026-07-14T11:23:00Z");
    private static final Instant TURN_MUST_BE_CONFIRMED_BY = Instant.parse("2026-07-14T11:18:00Z");

    @Test
    void movingTurnaroundAtTerminalIsConfirmedWithinMinutesNotSix() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-160-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-160-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d0, d1);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        List<GpsFix> fixes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Case160GurtlyTurnLagRegressionTest.class.getResourceAsStream(
                        "/fixtures/replay/case160-6303agj-20260714.jsonl"),
                StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.isBlank()) continue;
                JsonNode j = mapper.readTree(ln);
                fixes.add(new GpsFix(j.get("vehicleId").asText(),
                        j.path("licensePlate").asText(""), "160",
                        j.get("latitude").asDouble(), j.get("longitude").asDouble(),
                        j.path("speedKmh").asDouble(0), j.path("course").asDouble(0),
                        j.path("inMotion").asBoolean(false),
                        Instant.parse(j.get("timestamp").asText()),
                        j.path("direction").asInt(0), null, null, null,
                        Instant.parse(j.get("timestamp").asText())));
            }
        }
        assertThat(fixes).hasSizeGreaterThan(200);

        Instant turnConfirmedAt = null;
        long tripIdBeforeTurn = -1;
        int directionChangesAfterTurn = 0;
        for (GpsFix fx : fixes) {
            if (fx.timestamp().isBefore(PHYSICAL_TURN_STARTED)) {
                core.onFix(fx, topo);
                tripIdBeforeTurn = core.tripId();
                continue;
            }
            int dirBefore = core.direction();
            core.onFix(fx, topo);
            if (core.direction() != dirBefore) {
                if (turnConfirmedAt == null && core.direction() == 1) {
                    turnConfirmedAt = fx.timestamp();
                } else {
                    directionChangesAfterTurn++;
                }
            }
        }

        assertThat(core.direction())
                .as("после разворота 11:15-11:17 ведение по d1")
                .isEqualTo(1);

        assertThat(turnConfirmedAt)
                .as("разворот ходом засчитан")
                .isNotNull();
        assertThat(turnConfirmedAt)
                .as("смена d0→d1 в минуты разворота, а не 6+ минут пятящегося RECOVERING (жило до %s)",
                        LIVE_MODEL_STILL_BACKWARDS_UNTIL)
                .isBefore(TURN_MUST_BE_CONFIRMED_BY);

        assertThat(directionChangesAfterTurn)
                .as("после разворота направление не флапает")
                .isZero();

        assertThat(core.tripId() - tripIdBeforeTurn)
                .as("разворот даёт ровно одну границу рейса")
                .isEqualTo(1);
    }
}
