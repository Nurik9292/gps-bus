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

class StarvedParallelTurnGateU7PrimeTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
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

    private record Step(String mode, long tripId, GpsFix fix) {}

    @Test
    void starvedParallelWithThreeAxisTouchesResolvesViaOffRouteNotTerminalTurn() {
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
        for (GpsFix fx : arrival) {
            core.onFix(fx, topo);
        }
        long tripBefore = core.tripId();

        List<GpsFix> run = new ArrayList<>();
        t += 60;
        run.add(fixOffAxis(G61_0, 150, 190.0, 15.0, t));
        t += 60;
        run.add(fixOffAxis(G61_0, 400, 190.0, 15.0, t));
        long[] touchTimes = new long[3];
        double[] touchS = {900, 1150, 1400};
        for (int i = 0; i < 3; i++) {
            t += 60;
            touchTimes[i] = t;
            run.add(fixOnAxis(G61_0, touchS[i], 15.0, t));
        }
        for (double s = 1650; s <= 4100; s += 250) {
            t += 60;
            run.add(fixOffAxis(G61_0, s, 190.0, 15.0, t));
        }

        List<Step> steps = new ArrayList<>();
        StringBuilder spot = new StringBuilder();
        double lastSnapMs = Double.NaN;
        for (GpsFix fx : run) {
            double tauIn = Double.isNaN(core.lastLeaderSnapAtMs()) ? 0
                    : Math.max(0, (fx.timestamp().toEpochMilli() - core.lastLeaderSnapAtMs()) / 1000.0);
            double wEff = MotionFilterCore.effectiveTurnWindow(
                    CFG.wTurnWindowMeters(), CFG.turnVTargetMs(), tauIn,
                    CFG.turnTauNomSec(), CFG.wTurnWindowMaxMeters());
            var est = core.onFix(fx, topo);
            steps.add(new Step(est.mode(), core.tripId(), fx));
            for (int i = 0; i < 3; i++) {
                if (fx.timestamp().getEpochSecond() == touchTimes[i]) {
                    spot.append(String.format(
                            "касание-%d s=%.0f: τ_in=%.0fс v̂=%.1f s_base(600)=%s, s_wEff(%.0f)=%s, mode-после=%s, tripId=%d%n",
                            i + 1, touchS[i], tauIn, Math.abs(core.modelSpeedMs()),
                            touchS[i] > 600 ? "ВНЕ" : "внутри",
                            wEff, touchS[i] <= wEff ? "В ОКНЕ" : "вне", est.mode(), core.tripId()));
                }
            }
            lastSnapMs = core.lastLeaderSnapAtMs();
        }
        System.out.print(spot);
        System.out.printf("итог: цепочка mode=%s, tripId %d→%d%n",
                steps.stream().map(Step::mode).distinct().toList(), tripBefore, core.tripId());

        List<String> modes = steps.stream().map(Step::mode).toList();
        int firstOffRoute = modes.indexOf("OFF_ROUTE");
        int firstNewTrip = modes.indexOf("NEW_TRIP");
        assertThat(firstOffRoute)
                .as("U7′: разрешение через OFF_ROUTE (pin-cap), не терминальный щелчок")
                .isPositive();
        if (firstNewTrip >= 0) {
            assertThat(firstNewTrip)
                    .as("NEW_TRIP только ПОСЛЕ OFF_ROUTE (контракт lagDeparture), не ложный turn")
                    .isGreaterThan(firstOffRoute);
        }
        assertThat(modes.subList(0, firstOffRoute))
                .as("до OFF_ROUTE нет ложного терминального NEW_TRIP")
                .noneMatch(m -> m.equals("NEW_TRIP"));
    }
}
