package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class Case57ReplayTest {

    @Test
    @EnabledIfSystemProperty(named = "case57.fixes", matches = ".+")
    void replayCase57EpisodeOnCurrentDefaults() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d1, d0);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();
        ObjectMapper mapper = new ObjectMapper();
        List<GpsFix> fixes = new ArrayList<>();
        try (BufferedReader r = Files.newBufferedReader(Path.of(System.getProperty("case57.fixes")))) {
            String ln;
            while ((ln = r.readLine()) != null) {
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
        long ticks = 0;
        try (BufferedWriter w = Files.newBufferedWriter(Path.of(System.getProperty("case57.dump")))) {
            w.write("vid8|ts|mode|leader|s|dir|tripId|partial\n");
            for (GpsFix fx : fixes) {
                var est = core.onFix(fx, topo);
                w.write(String.format(Locale.ROOT, "%s|%s|%s|%s|%.1f|%d|%d|%s%n",
                        fx.vehicleId().substring(0, 8), fx.timestamp(), est.mode(),
                        core.bank().leader().variantId(), est.s(), core.direction(),
                        core.tripId(), core.currentTripPartial() ? "y" : "n"));
                ticks++;
            }
        }
        System.out.printf("case57: фиксов=%d тиков=%d%n", fixes.size(), ticks);
        assertThat(ticks).isPositive();
    }
}
