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

class Case20DirectionFlapRegressionTest {

    private static final Instant STABLE_D1_WINDOW_FROM = Instant.parse("2026-07-15T08:25:00Z");
    private static final Instant HONEST_TURNAROUND_DONE_BY = Instant.parse("2026-07-15T08:25:00Z");

    private record DirChange(Instant at, int fromDir, int toDir) {
    }

    @Test
    void frontageAxisRecoveringDipsDoNotFlipDirection() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-20-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-20-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d0, d1);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        List<GpsFix> fixes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                Case20DirectionFlapRegressionTest.class.getResourceAsStream(
                        "/fixtures/replay/case20-1356agj-20260715.jsonl"),
                StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.isBlank()) continue;
                JsonNode j = mapper.readTree(ln);
                fixes.add(new GpsFix(j.get("vehicleId").asText(),
                        j.path("licensePlate").asText(""), "20",
                        j.get("latitude").asDouble(), j.get("longitude").asDouble(),
                        j.path("speedKmh").asDouble(0), j.path("course").asDouble(0),
                        j.path("inMotion").asBoolean(false),
                        Instant.parse(j.get("timestamp").asText()),
                        j.path("direction").asInt(0), null, null, null,
                        Instant.parse(j.get("timestamp").asText())));
            }
        }
        assertThat(fixes).hasSizeGreaterThan(150);

        List<DirChange> dirChanges = new ArrayList<>();
        int prevDir = core.direction();
        for (GpsFix fx : fixes) {
            core.onFix(fx, topo);
            if (core.direction() != prevDir) {
                dirChanges.add(new DirChange(fx.timestamp(), prevDir, core.direction()));
                prevDir = core.direction();
            }
        }

        List<DirChange> lateChanges = dirChanges.stream()
                .filter(c -> c.at().isAfter(STABLE_D1_WINDOW_FROM))
                .toList();
        assertThat(lateChanges)
                .as("борт стабильно на d1 после 08:25 — ложных флапов на frontage быть не должно (было 2 в 08:39/08:40)")
                .isEmpty();

        assertThat(core.direction())
                .as("в конце вырезки ведение по d1")
                .isEqualTo(1);

        boolean honestTurnCounted = dirChanges.stream()
                .anyMatch(c -> c.toDir() == 1 && c.at().isBefore(HONEST_TURNAROUND_DONE_BY));
        assertThat(honestTurnCounted)
                .as("честный разворот d0→d1 (08:10-08:18) засчитан, фикс не «замораживает» банк")
                .isTrue();

        assertThat(dirChanges.size())
                .as("направление меняется только по-настоящему (одна честная смена, без флап-пар)")
                .isLessThanOrEqualTo(2);
    }
}
