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

class FreezeReanchorScenariosTest {

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();

    private static GpsFix fixOn(GeometryFixture g, double s, double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return new GpsFix("test-veh", "TEST 01", "61", p[0], p[1],
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

    private static long arriveAtGurtlyTerminal(MotionFilterCore core, RouteTopology topo) {
        List<GpsFix> arrival = new ArrayList<>();
        long t = 1000;
        for (double s = 26500; s <= 26810; s += 40, t += 5) {
            arrival.add(fixOn(G61_1, Math.min(s, G61_1.totalMeters()), 25.0, t));
        }
        for (int i = 0; i < 6; i++, t += 5) {
            arrival.add(fixOn(G61_1, G61_1.totalMeters(), 6.0, t));
        }
        List<Step> steps = drive(core, topo, arrival);
        assertThat(steps.get(steps.size() - 1).mode()).isEqualTo("AT_TERMINAL");
        return t;
    }

    @Test
    void throughGapBoundaryRestoredWithinThreeTicksAsPartialTrip() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = arriveAtGurtlyTerminal(core, topo);
        long tripBefore = core.tripId();

        t += 102;
        List<GpsFix> postGap = new ArrayList<>();
        for (double s = 60; s <= 600; s += 60, t += 5) {
            postGap.add(fixOn(G61_0, s, 30.0, t));
        }
        List<Step> steps = drive(core, topo, postGap);

        int newTripIdx = steps.stream().map(Step::mode).toList().indexOf("NEW_TRIP");
        assertThat(newTripIdx).as("граница восстановлена").isNotNegative();
        assertThat(newTripIdx + 1)
                .as("NEW_TRIP ≤ freeze-тик + посев + kConfirmFreeze прогрессов + pending-тик")
                .isLessThanOrEqualTo(3 + CFG.kConfirmFreeze());
        assertThat(core.tripId()).isEqualTo(tripBefore + 1);
        assertThat(core.direction()).isEqualTo(0);
    }

    @Test
    void deepGapReanchorMidRouteEmitsPartialNewTrip() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = arriveAtGurtlyTerminal(core, topo);
        long tripBefore = core.tripId();

        t += 900;
        List<GpsFix> postGap = new ArrayList<>();
        for (double s = 15500; s <= 16700; s += 60, t += 5) {
            postGap.add(fixOn(G61_0, s, 40.0, t));
        }
        List<Step> steps = drive(core, topo, postGap);

        assertThat(steps).anyMatch(st -> st.mode().equals("NEW_TRIP"));
        assertThat(core.tripId()).isEqualTo(tripBefore + 1);
        assertThat(core.currentTripPartial())
                .as("глубокая freeze-ре-привязка со сменой d — NEW_TRIP с меткой partial").isTrue();
        assertThat(core.direction()).isEqualTo(0);
    }

    @Test
    void foldFalseReanchorWithoutProgressDoesNotIncrementTripId() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<GpsFix> ride = new ArrayList<>();
        long t = 1000;
        for (double s = 18000; s <= 19000; s += 60, t += 5) {
            ride.add(fixOn(G61_1, s, 40.0, t));
        }
        drive(core, topo, ride);
        long tripBefore = core.tripId();

        t += 431;
        List<GpsFix> postGap = new ArrayList<>();
        for (double s = 21000; s <= 22400; s += 60, t += 5) {
            postGap.add(fixOn(G61_1, s, 40.0, t));
        }
        List<Step> steps = drive(core, topo, postGap);

        assertThat(core.tripId())
                .as("ре-привязка в складке без подтверждённого прогресса чужой оси — без trip_id++")
                .isEqualTo(tripBefore);
        assertThat(core.direction()).isEqualTo(1);
        assertThat(steps).noneMatch(st -> st.mode().equals("NEW_TRIP"));
    }

    @Test
    void gapOnSameAxisKeepsTripAndRecoversToTracking() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<GpsFix> ride = new ArrayList<>();
        long t = 1000;
        for (double s = 8000; s <= 9000; s += 60, t += 5) {
            ride.add(fixOn(G61_1, s, 40.0, t));
        }
        drive(core, topo, ride);
        long tripBefore = core.tripId();

        t += 180;
        List<GpsFix> postGap = new ArrayList<>();
        for (double s = 11000; s <= 12000; s += 60, t += 5) {
            postGap.add(fixOn(G61_1, s, 40.0, t));
        }
        List<Step> steps = drive(core, topo, postGap);

        assertThat(core.tripId()).as("гэп без смены d — прежнее поведение").isEqualTo(tripBefore);
        assertThat(steps.get(steps.size() - 1).mode()).isEqualTo("TRACKING");
        assertThat(steps).noneMatch(st -> st.mode().equals("NEW_TRIP"));
    }
}
