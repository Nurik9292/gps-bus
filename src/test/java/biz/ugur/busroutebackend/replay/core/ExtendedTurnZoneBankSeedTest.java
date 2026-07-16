package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.HypothesisBank;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtendedTurnZoneBankSeedTest {

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

    private static long[] arriveGurtly(MotionFilterCore core, RouteTopology topo) {
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
        return new long[]{t, core.tripId()};
    }

    private static HypothesisBank.Hypothesis oppHypothesis(MotionFilterCore core, int direction) {
        return core.bank().hypotheses().stream()
                .filter(h -> h.direction() == direction)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void starvedExtendedZoneFixReanchorsMainOppositeHypothesis() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        long[] arrived = arriveGurtly(core, topo);
        long tripBefore = arrived[1];

        var est = core.onFix(fixNearAxis(G61_0, 800, 40.0, 15.0, arrived[0] + 60), topo);

        HypothesisBank.Hypothesis opp = oppHypothesis(core, G61_0.direction());
        assertThat(opp.snappedLast())
                .as("τ=60>τ_nom, s=800 в (600; wEff]: main-опп гипотеза пере-анкерована в гейт-точку")
                .isTrue();
        assertThat(opp.x())
                .as("посев ставит опп-гипотезу в точку расширенного снапа")
                .isBetween(700.0, 900.0);
        assertThat(est.mode())
                .as("посев не трогает mode терминального канала")
                .isNotIn("TURNING", "NEW_TRIP");
        assertThat(core.tripId())
                .as("посев без подтверждения: tripId не меняется")
                .isEqualTo(tripBefore);
    }

    @Test
    void freshLeaderKeepsBaseWindowAndDoesNotSeed() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        long[] arrived = arriveGurtly(core, topo);
        long tripBefore = arrived[1];

        var est = core.onFix(fixNearAxis(G61_0, 800, 40.0, 15.0, arrived[0] + 20), topo);

        HypothesisBank.Hypothesis opp = oppHypothesis(core, G61_0.direction());
        boolean anchoredAtExtendedZone = opp.snappedLast() && opp.x() > 700.0;
        assertThat(anchoredAtExtendedZone)
                .as("τ=20<=τ_nom: окно=база, посева нет — опп-гипотеза не якорится на 800")
                .isFalse();
        assertThat(est.mode()).isNotIn("TURNING", "NEW_TRIP");
        assertThat(core.tripId()).isEqualTo(tripBefore);
    }

    @Test
    void seededBankConfirmsTurnOnlyThroughTwoPhaseCommitWithSingleTripIncrement() {
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(CFG);
        long[] arrived = arriveGurtly(core, topo);
        long tripBefore = arrived[1];
        long t = arrived[0];

        List<String> modes = new ArrayList<>();
        for (double s = 650; s <= 5150; s += 250) {
            t += 60;
            modes.add(core.onFix(fixNearAxis(G61_0, s, 60.0, 15.0, t), topo).mode());
        }
        System.out.printf("δ3-добор: цепочка=%s tripId %d→%d%n",
                modes, tripBefore, core.tripId());

        assertThat(modes)
                .as("δ3: терминальный канал в расширенной зоне молчит — TURNING нет")
                .noneMatch(m -> m.equals("TURNING"));
        assertThat(modes)
                .as("δ3-добор: банковский путь доводит до NEW_TRIP")
                .contains("NEW_TRIP");
        assertThat(core.tripId() - tripBefore)
                .as("граница рейса ровно одна")
                .isEqualTo(1);
    }
}
