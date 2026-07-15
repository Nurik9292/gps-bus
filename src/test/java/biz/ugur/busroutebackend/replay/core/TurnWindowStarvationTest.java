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

class TurnWindowStarvationTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double BASE = CFG.wTurnWindowMeters();
    private static final double TAU_NOM = CFG.turnTauNomSec();
    private static final double V_TARGET = CFG.turnVTargetMs();
    private static final double CAP = CFG.wTurnWindowMaxMeters();

    private static double w(double tau) {
        return MotionFilterCore.effectiveTurnWindow(BASE, V_TARGET, tau, TAU_NOM, CAP);
    }

    @Test
    void u1TauAtOrBelowNomYieldsExactBase() {
        assertThat(w(0)).isEqualTo(BASE);
        assertThat(w(TAU_NOM)).isEqualTo(BASE);
        assertThat(w(TAU_NOM - 0.001)).isEqualTo(BASE);
    }

    @Test
    void u3CapReachedExactlyAndNeverExceeded() {
        assertThat(w(457)).isEqualTo(CAP);
        assertThat(w(458)).isEqualTo(CAP);
        assertThat(w(1e9)).isEqualTo(CAP);
    }

    @Test
    void u5MonotoneNonDecreasingInTauWithinCap() {
        double prev = -1;
        for (double tau = 0; tau <= 600; tau += 5) {
            double cur = w(tau);
            assertThat(cur).isGreaterThanOrEqualTo(prev);
            assertThat(cur).isLessThanOrEqualTo(CAP);
            prev = cur;
        }
    }

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final double DEG_PER_METER_LAT = 1.0 / 111320.0;

    private static GpsFix fixOnAxis(GeometryFixture g, double s, double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return new GpsFix("test-veh", "TEST 01", "61", p[0], p[1],
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), 0,
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private static GpsFix fixOffAxis(GeometryFixture g, double s, double offsetMeters,
                                     double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return new GpsFix("test-veh", "TEST 01", "61",
                p[0] + offsetMeters * DEG_PER_METER_LAT, p[1],
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), 0,
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private record Step(String mode, long tripId) {}

    private static List<Step> drive(MotionFilterCore core, RouteTopology topo, List<GpsFix> fixes) {
        List<Step> out = new ArrayList<>();
        for (GpsFix fx : fixes) {
            out.add(new Step(core.onFix(fx, topo).mode(), core.tripId()));
        }
        return out;
    }

    @Test
    void u7OffRouteDuringStarvationStaysOffRoute() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<GpsFix> arrival = new ArrayList<>();
        long t = 1000;
        for (double s = 26500; s <= 26810; s += 40, t += 5) {
            arrival.add(fixOnAxis(G61_1, Math.min(s, G61_1.totalMeters()), 25.0, t));
        }
        for (int i = 0; i < 6; i++, t += 5) {
            arrival.add(fixOnAxis(G61_1, G61_1.totalMeters(), 6.0, t));
        }
        List<Step> steps = drive(core, topo, arrival);
        assertThat(steps.get(steps.size() - 1).mode()).isEqualTo("AT_TERMINAL");
        long tripBefore = core.tripId();

        List<GpsFix> starvedField = new ArrayList<>();
        for (double s = 100; s <= 2500; s += 240, t += 60) {
            starvedField.add(fixOffAxis(G61_0, s, 190.0, 15.0, t));
            if (Math.abs(s - 1540) < 1) {
                starvedField.add(fixOnAxis(G61_0, 1500, 15.0, t + 30));
                t += 30;
            }
        }
        List<Step> offSteps = drive(core, topo, starvedField);

        int firstOffRoute = offSteps.stream().map(Step::mode).toList().indexOf("OFF_ROUTE");
        assertThat(firstOffRoute)
                .as("истинный уход при голоде (τ>>τ_nom, широкое окно): OFF_ROUTE наступает")
                .isPositive();
        assertThat(offSteps)
                .as("широкое окно НЕ поглощает уход ложным разворотом: NEW_TRIP отсутствует")
                .noneMatch(st -> st.mode().equals("NEW_TRIP"));
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }
}
