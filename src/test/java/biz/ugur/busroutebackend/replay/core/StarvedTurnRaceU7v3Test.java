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

class StarvedTurnRaceU7v3Test {

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

    private static GpsFix fixNearAxis(GeometryFixture g, double s, double offsetMeters,
                                      double speedKmh, long tSec) {
        double[] p = g.pointAtS(s);
        return new GpsFix("test-veh", "TEST 01", "61",
                p[0] + offsetMeters * DEG_PER_METER_LAT, p[1],
                speedKmh, 0.0, speedKmh > 1, Instant.ofEpochSecond(tSec), 0,
                0.8, 12, 0.0, Instant.ofEpochSecond(tSec));
    }

    private static MotionFilterCore arriveGurtly(RouteTopology topo) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long t = 1000;
        List<GpsFix> arrival = new ArrayList<>();
        for (double s = 26500; s <= 26810; s += 40, t += 5) {
            arrival.add(fixOnAxis(G61_1, Math.min(s, G61_1.totalMeters()), 25.0, t));
        }
        for (int i = 0; i < 6; i++, t += 5) {
            arrival.add(fixOnAxis(G61_1, G61_1.totalMeters(), 6.0, t));
        }
        for (GpsFix fx : arrival) {
            core.onFix(fx, topo);
        }
        return core;
    }

    private static long lastArrivalSec() {
        long t = 1000;
        for (double s = 26500; s <= 26810; s += 40) t += 5;
        return t + 6 * 5;
    }

    @Test
    void u7v3RaceStarvedMonotoneWithinLateralMustResolveViaOffRoute() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = arriveGurtly(topo);
        long tripBefore = core.tripId();
        long t = lastArrivalSec();

        List<GpsFix> run = new ArrayList<>();
        double[] sSeq = {650, 900, 1150, 1400, 1650, 1900, 2150, 2400, 2650, 2900};
        for (double s : sSeq) {
            t += 60;
            run.add(fixNearAxis(G61_0, s, 40.0, 15.0, t));
        }

        StringBuilder race = new StringBuilder();
        List<String> modes = new ArrayList<>();
        String prevMode = "AT_TERMINAL";
        int tick = 0;
        Integer turnConfirmTick = null, offRouteTick = null, newTripTick = null;
        double prevS = Double.NaN;
        for (GpsFix fx : run) {
            tick++;
            double tauIn = Double.isNaN(core.lastLeaderSnapAtMs()) ? 0
                    : Math.max(0, (fx.timestamp().toEpochMilli() - core.lastLeaderSnapAtMs()) / 1000.0);
            double wEff = MotionFilterCore.effectiveTurnWindow(
                    CFG.wTurnWindowMeters(), CFG.turnVTargetMs(), tauIn,
                    CFG.turnTauNomSec(), CFG.wTurnWindowMaxMeters());
            double s = sSeq[tick - 1];
            double ds = Double.isNaN(prevS) ? 0 : s - prevS;
            var est = core.onFix(fx, topo);
            modes.add(est.mode());
            String event = "-";
            if (!est.mode().equals(prevMode)) {
                event = prevMode + "→" + est.mode();
                if (est.mode().equals("TURNING") && turnConfirmTick == null) turnConfirmTick = tick;
                if (est.mode().equals("OFF_ROUTE") && offRouteTick == null) offRouteTick = tick;
                if (est.mode().equals("NEW_TRIP") && newTripTick == null) newTripTick = tick;
            }
            race.append(String.format(
                    "тик-%d t=+%dс: τ_in=%.0f W_eff=%.0f s=%.0f s_base=%s s_wEff=%s Δs=%.0f(≥25:%s) "
                            + "lat=40(≤80:да) mode=%s trip=%d событие=%s%n",
                    tick, tick * 60, tauIn, wEff, s,
                    s > 600 ? "ВНЕ" : "в", s <= wEff ? "В" : "вне", ds, ds >= 25 ? "да" : "нет",
                    est.mode(), core.tripId(), event));
            prevMode = est.mode();
            prevS = s;
        }
        System.out.print(race);
        System.out.printf("ГОНКА: turn-confirm(TURNING)=тик %s; OFF_ROUTE=тик %s; NEW_TRIP=тик %s%n",
                turnConfirmTick, offRouteTick, newTripTick);

        int firstOffRoute = modes.indexOf("OFF_ROUTE");
        int firstNewTrip = modes.indexOf("NEW_TRIP");
        int firstTurning = modes.indexOf("TURNING");
        assertThat(firstOffRoute)
                .as("U7'v3: разрешение через OFF_ROUTE (pin-cap)")
                .isPositive();
        assertThat(firstTurning < 0 || firstTurning > firstOffRoute)
                .as("ложный terminalTurn НЕ выигрывает гонку у OFF_ROUTE")
                .isTrue();
        if (firstNewTrip >= 0) {
            assertThat(firstNewTrip).isGreaterThan(firstOffRoute);
        }
        assertThat(core.tripId() - tripBefore)
                .as("tripId контрактом (не терминальным щелчком до OFF_ROUTE)")
                .isLessThanOrEqualTo(1);
    }

    @Test
    void positiveDensePathIdenticalToBaseWindow() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = arriveGurtly(topo);
        long tripBefore = core.tripId();
        long t = lastArrivalSec();
        List<String> modes = new ArrayList<>();
        for (double s = 650; s <= 2900; s += 45, t += 5) {
            var est = core.onFix(fixNearAxis(G61_0, s, 40.0, 30.0, t), topo);
            modes.add(est.mode());
        }
        assertThat(modes)
                .as("без голода (τ<=25) окно=база: касания s>600 вне окна, NEW_TRIP до OFF_ROUTE нет")
                .satisfies(ms -> {
                    int ot = ms.indexOf("OFF_ROUTE");
                    int nt = ms.indexOf("NEW_TRIP");
                    assertThat(nt < 0 || (ot >= 0 && nt > ot)).isTrue();
                });
        assertThat(core.tripId() - tripBefore).isLessThanOrEqualTo(1);
    }

    @Test
    void stationaryUnderWideWindowNeverConfirmsTurn() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = arriveGurtly(topo);
        long tripBefore = core.tripId();
        long t = lastArrivalSec();
        List<String> modes = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            t += 60;
            var est = core.onFix(fixNearAxis(G61_0, 700, 40.0, 0.0, t), topo);
            modes.add(est.mode());
        }
        assertThat(modes)
                .as("стоячий (Δs<θ) под широким окном: 0 turn-confirm, TURNING/NEW_TRIP нет")
                .noneMatch(m -> m.equals("TURNING") || m.equals("NEW_TRIP"));
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }
}
