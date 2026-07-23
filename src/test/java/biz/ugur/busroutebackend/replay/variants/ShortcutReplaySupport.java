package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

final class ShortcutReplaySupport {

    record DivergenceReport(double maxDivergenceMeters,
                            long maxContinuousSecondsAbove,
                            int directionChanges,
                            long tripIncrements,
                            long shortcutJumps,
                            String finalMode) {
    }

    private ShortcutReplaySupport() {
    }

    static List<GpsFix> loadFixes(String resource, String routeNumber) throws Exception {
        List<GpsFix> fixes = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                ShortcutReplaySupport.class.getResourceAsStream(resource),
                StandardCharsets.UTF_8))) {
            String ln;
            while ((ln = r.readLine()) != null) {
                if (ln.isBlank()) continue;
                JsonNode j = mapper.readTree(ln);
                fixes.add(new GpsFix(j.get("vehicleId").asText(),
                        j.path("licensePlate").asText(""), routeNumber,
                        j.get("latitude").asDouble(), j.get("longitude").asDouble(),
                        j.path("speedKmh").asDouble(0), j.path("course").asDouble(0),
                        j.path("inMotion").asBoolean(false),
                        Instant.parse(j.get("timestamp").asText()),
                        j.path("direction").asInt(0), null, null, null,
                        Instant.parse(j.get("timestamp").asText())));
            }
        }
        return fixes;
    }

    static DivergenceReport replay(String routeNumber, String fixesResource,
                                   Instant windowFrom, Instant windowTo,
                                   double divergenceThresholdMeters) throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath(
                "/fixtures/geometry/route-" + routeNumber + "-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath(
                "/fixtures/geometry/route-" + routeNumber + "-dir1.json");
        RouteTopology topo = RouteTopology.thereAndBack(d0, d1);
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        List<GpsFix> fixes = loadFixes(fixesResource, routeNumber);

        double maxDivergence = 0;
        long maxContinuous = 0;
        Instant aboveSince = null;
        Integer prevDir = null;
        int dirChanges = 0;
        long tripStart = -1;
        long tripEnd = -1;
        Instant prevTs = null;
        String finalMode = "";
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
            finalMode = est.mode();
            boolean inWindow = !fx.timestamp().isBefore(windowFrom)
                    && !fx.timestamp().isAfter(windowTo);
            if (inWindow && prevDir != null && core.direction() != prevDir) {
                dirChanges++;
            }
            prevDir = core.direction();
            if (inWindow && tripStart < 0) {
                tripStart = core.tripId();
            }
            if (!inWindow) {
                if (tripStart >= 0 && tripEnd < 0) {
                    tripEnd = core.tripId();
                }
                aboveSince = null;
                continue;
            }
            RouteLine g = topo.geom(core.direction());
            double[] proj = g.pointAtS(Math.min(est.s(), g.totalMeters()));
            double divergence = RouteLine.haversineMeters(
                    fx.latitude(), fx.longitude(), proj[0], proj[1]);
            maxDivergence = Math.max(maxDivergence, divergence);
            if (divergence > divergenceThresholdMeters) {
                if (aboveSince == null) {
                    aboveSince = fx.timestamp();
                }
                maxContinuous = Math.max(maxContinuous,
                        Duration.between(aboveSince, fx.timestamp()).toSeconds());
            } else {
                aboveSince = null;
            }
        }
        long tripsInWindow = (tripEnd >= 0 ? tripEnd : core.tripId())
                - Math.max(tripStart, 0);
        return new DivergenceReport(maxDivergence, maxContinuous, dirChanges,
                tripsInWindow, core.shortcutJumps(), finalMode);
    }
}
