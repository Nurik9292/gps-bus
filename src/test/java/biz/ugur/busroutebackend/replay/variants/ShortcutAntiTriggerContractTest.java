package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShortcutAntiTriggerContractTest {

    private static final Instant T0 = Instant.parse("2026-07-23T05:00:00Z");

    private RouteTopology topology() throws Exception {
        GeometryFixture d0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir0.json");
        GeometryFixture d1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-57-dir1.json");
        return RouteTopology.thereAndBack(d0, d1);
    }

    private GpsFix fix(double lat, double lon, double speedKmh, Instant ts) {
        return new GpsFix("veh-sc", "0001 TST", "57",
                lat, lon, speedKmh, 0, speedKmh > 2, ts, 0, null, null, null, ts);
    }

    @Test
    void ordinaryDrivingAlongAxisNeverTriggersShortcut() throws Exception {
        RouteTopology topo = topology();
        RouteLine g = topo.first();
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        Instant t = T0;
        double s = 5000;
        for (int i = 0; i < 40; i++) {
            double[] p = g.pointAtS(s);
            core.onFix(fix(p[0], p[1], 40, t), topo);
            t = t.plusSeconds(20);
            s += 220;
        }
        assertThat(core.shortcutJumps())
                .as("обычная езда вдоль оси не триггерит канал")
                .isZero();
    }

    @Test
    void stationaryBusNearAxisNeverTriggersShortcut() throws Exception {
        RouteTopology topo = topology();
        RouteLine g = topo.first();
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        double[] p = g.pointAtS(8000);
        Instant t = T0;
        for (int i = 0; i < 30; i++) {
            core.onFix(fix(p[0] + 0.00005, p[1], 0, t), topo);
            t = t.plusSeconds(20);
        }
        assertThat(core.shortcutJumps())
                .as("стоячий борт (GPS-шум) не триггерит канал")
                .isZero();
    }

    @Test
    void gpsNoiseJitterDoesNotAccumulateStreak() throws Exception {
        RouteTopology topo = topology();
        RouteLine g = topo.first();
        MotionFilterCore core = new MotionFilterCore(CoreConfig.defaults());
        core.reset();

        Instant t = T0;
        double s = 6000;
        for (int i = 0; i < 30; i++) {
            double jitterLat = (i % 2 == 0 ? 0.0006 : -0.0006);
            double[] p = g.pointAtS(s);
            core.onFix(fix(p[0] + jitterLat, p[1], 25, t), topo);
            t = t.plusSeconds(20);
            s += 140;
        }
        assertThat(core.shortcutJumps())
                .as("поперечный GPS-дребезг ~65 м не собирает серию")
                .isZero();
    }
}
