package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CloseTailAndTerminalGateScenariosTest {

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();

    private static GpsFix fixOn(GeometryFixture g, double s, double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return new GpsFix("test-veh", "TEST 01", g.routeNumber(), p[0], p[1],
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), g.direction(),
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private static String drive(MotionFilterCore core, RouteTopology topo, List<GpsFix> fixes) {
        String lastMode = "";
        for (GpsFix fx : fixes) {
            lastMode = core.onFix(fx, topo).mode();
        }
        return lastMode;
    }

    @Test
    void closingDirectionSwitchAtOwnTerminalDoesNotIncrementTripId() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_0, G61_1);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<GpsFix> fixes = new ArrayList<>();
        long t = 1000;
        for (double s = 320; s >= 60; s -= 20, t += 5) {
            fixes.add(fixOn(G61_0, s, 14.5, t));
        }
        drive(core, topo, fixes);

        assertThat(core.tripId()).as("закрывающая смена у L_d1 — без trip_id++").isEqualTo(1);
        assertThat(core.direction()).as("ведение скорректировано на d1").isEqualTo(1);
        assertThat(core.bank().leader().variantId()).isEqualTo("61#d1");
    }

    @Test
    void depotManeuversAtPinnedTerminalDoNotSwitchLeader() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<GpsFix> arrival = new ArrayList<>();
        long t = 1000;
        for (double s = 26500; s <= 26810; s += 40, t += 5) {
            arrival.add(fixOn(G61_1, Math.min(s, G61_1.totalMeters()), 25.0, t));
        }
        for (int i = 0; i < 6; i++, t += 5) {
            arrival.add(fixOn(G61_1, G61_1.totalMeters(), 6.0, t));
        }
        String modeAfterArrival = drive(core, topo, arrival);
        assertThat(modeAfterArrival).isEqualTo("AT_TERMINAL");
        long tripBefore = core.tripId();

        List<GpsFix> maneuvers = new ArrayList<>();
        for (int series = 0; series < 4; series++) {
            for (double s = 20; s <= 80; s += 30, t += 5) {
                maneuvers.add(fixOn(G61_0, s, 12.0, t));
            }
            t += 20;
            maneuvers.add(fixOn(G61_0, 15, 0.0, t));
            t += 10;
        }
        drive(core, topo, maneuvers);

        assertThat(core.tripId()).as("манёвры по площадке — без trip_id++").isEqualTo(tripBefore);
        assertThat(core.bank().leader().variantId())
                .as("гейт №23′ держит лидера d1 у пина").isEqualTo("61#d1");
    }

    @Test
    void midlineDirectionSwitchStillEmitsNewTrip() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_0, G61_1);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<GpsFix> fixes = new ArrayList<>();
        long t = 1000;
        for (double s = 5000; s <= 6000; s += 60, t += 5) {
            fixes.add(fixOn(G61_0, s, 40.0, t));
        }
        for (double s = 6000; s >= 4200; s -= 60, t += 5) {
            fixes.add(fixOn(G61_0, s, 40.0, t));
        }
        drive(core, topo, fixes);

        assertThat(core.tripId()).as("штатная смена d вне пина — NEW_TRIP + trip_id++").isEqualTo(2);
        assertThat(core.direction()).isEqualTo(1);
    }
}
