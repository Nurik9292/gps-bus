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

class TerminalDepartureOffRouteScenariosTest {

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();

    private static GpsFix fixAt(double lat, double lon, double speedKmh, long tSec) {
        return new GpsFix("test-veh", "TEST 01", "61", lat, lon,
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), 0,
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private static GpsFix fixOnAxis(GeometryFixture g, double s, double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return fixAt(p[0], p[1], speedKmh, tSec);
    }

    private static GpsFix fixOffAxis(GeometryFixture g, double s, double offsetMeters,
                                     double speedKmh, long tSec) {
        double[] a = g.pointAtS(s);
        double[] b = g.pointAtS(s + 10);
        double dLat = b[0] - a[0];
        double dLon = (b[1] - a[1]) * Math.cos(Math.toRadians(a[0]));
        double len = Math.hypot(dLat, dLon);
        double nLat = -dLon / len;
        double nLon = dLat / len;
        double lat = a[0] + nLat * offsetMeters / 111320.0;
        double lon = a[1] + nLon * offsetMeters / (111320.0 * Math.cos(Math.toRadians(a[0])));
        return fixAt(lat, lon, speedKmh, tSec);
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
            arrival.add(fixOnAxis(G61_1, Math.min(s, G61_1.totalMeters()), 25.0, t));
        }
        for (int i = 0; i < 6; i++, t += 5) {
            arrival.add(fixOnAxis(G61_1, G61_1.totalMeters(), 6.0, t));
        }
        List<Step> steps = drive(core, topo, arrival);
        assertThat(steps.get(steps.size() - 1).mode()).isEqualTo("AT_TERMINAL");
        return t;
    }

    @Test
    void cleanDepartureThroughAxisStartNeverEntersOffRouteContract() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = arriveAtGurtlyTerminal(core, topo);
        long tripBefore = core.tripId();

        List<GpsFix> departure = new ArrayList<>();
        for (double s = 30; s <= 350; s += 40, t += 5) {
            departure.add(fixOnAxis(G61_0, s, 20.0, t));
        }
        List<Step> steps = drive(core, topo, departure);

        assertThat(steps).noneMatch(st -> st.mode().equals("OFF_ROUTE"));
        assertThat(steps).anyMatch(st -> st.mode().equals("NEW_TRIP"));
        int newTripIdx = steps.stream().map(Step::mode).toList().indexOf("NEW_TRIP");
        assertThat(30 + 40L * newTripIdx)
                .as("NEW_TRIP не позже s=300 нового плеча").isLessThanOrEqualTo(300);
        assertThat(core.tripId()).isEqualTo(tripBefore + 1);
    }

    @Test
    void lagDepartureViaParallelRoadPinCapThenOffRouteThenNewTrip() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = arriveAtGurtlyTerminal(core, topo);
        long tripBefore = core.tripId();

        List<GpsFix> offAxisRun = new ArrayList<>();
        for (double s = 100; s <= 2100; s += 80, t += 5) {
            offAxisRun.add(fixOffAxis(G61_0, s, 190.0, 15.0, t));
        }
        List<Step> offSteps = drive(core, topo, offAxisRun);

        int firstOffRoute = offSteps.stream().map(Step::mode).toList().indexOf("OFF_ROUTE");
        assertThat(firstOffRoute).as("выезд вне коридора уводит с пина").isPositive();
        assertThat(firstOffRoute + 1)
                .as("cap: пин ≤ kTermMissOffRoute+nDepMoveConfirm тиков движения")
                .isLessThanOrEqualTo(CFG.kTermMissOffRoute() + CFG.nDepMoveConfirm());
        assertThat(offSteps.subList(0, firstOffRoute))
                .noneMatch(st -> st.mode().equals("NEW_TRIP"));

        List<GpsFix> corridorRun = new ArrayList<>();
        for (double s = 2150; s <= 2900; s += 60, t += 5) {
            corridorRun.add(fixOnAxis(G61_0, s, 40.0, t));
        }
        List<Step> corSteps = drive(core, topo, corridorRun);

        assertThat(corSteps).anyMatch(st -> st.mode().equals("NEW_TRIP"));
        assertThat(core.tripId()).as("ровно один trip_id++ за выезд").isEqualTo(tripBefore + 1);
        assertThat(core.direction()).isEqualTo(0);
    }

    @Test
    void standingOrSlowManeuversAtDepotDoNotAccumulateMisses() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = arriveAtGurtlyTerminal(core, topo);

        List<GpsFix> standing = new ArrayList<>();
        for (int i = 0; i < 12; i++, t += 5) {
            standing.add(fixOffAxis(G61_0, 150, 190.0, 0.0, t));
        }
        for (int i = 0; i < 12; i++, t += 5) {
            standing.add(fixOffAxis(G61_0, 150 + (i % 3) * 15, 190.0, 3.0, t));
        }
        List<Step> steps = drive(core, topo, standing);

        assertThat(steps).noneMatch(st -> st.mode().equals("OFF_ROUTE"));
        assertThat(steps.get(steps.size() - 1).mode()).isEqualTo("AT_TERMINAL");
    }
}
