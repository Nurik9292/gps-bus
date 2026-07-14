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

class ParallelRoadTurnContractGateTest {

    private static final GeometryFixture G61_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    private static final GeometryFixture G61_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");
    private static final double DEG_PER_METER_LAT = 1.0 / 111320.0;

    private static CoreConfig pointW600N3() {
        CoreConfig c = CoreConfig.defaults();
        return new CoreConfig(c.dtSec(), c.w0Meters(), c.kWindowPerSpeed(), c.sigmaMeasDefaultMeters(),
                c.accuracyRefMeters(), c.dSnapMeters(), c.dMaxMeters(), c.gammaGate(),
                c.qPos(), c.qVel(), c.pInitPos(), c.pInitVel(), c.rMaxRate(), c.rMaxBaseMeters(),
                c.weakZvWeight(), c.nPersist(), c.mReanchor(), c.tLostSec(), c.tMaxSec(),
                c.vTargetMs(), c.aDepMs2(), c.aMaxMs2(), c.vMaxMs(), c.dReanchorMeters(),
                c.recoveryPullFactor(), c.vStopKmh(), c.vMoveKmh(), c.hStop(), c.hDep(), c.hDec(),
                c.dwellMinSec(), c.dDecelMeters(), c.epsArrMeters(), c.epsStopMeters(),
                c.epsTermMeters(), c.dwellExpectedSec(), c.dwellMaxSec(), 3,
                c.dTurnConfirmMeters(), c.unpinWindowTicks(), c.kTurnRevert(), 600.0,
                c.historyNMin(), c.kOffRoute(), c.mOffRouteExit(),
                c.scoreLambda(), c.scoreRejectPenalty(), c.scoreProgressBonus(),
                c.sSwitch(), c.hSwitch(), c.dSwitchSmoothMeters(), c.maxHypotheses(),
                c.rHdopEnabled(), c.rHdopAMeters(), c.rHdopBMetersPerHdop(),
                c.gateNisThreshold(), c.qScale(),
                c.epsCloseTailMeters(), c.nTurnConfirmTerm(), c.dTurnConfirmTermMeters(),
                c.kTermMissOffRoute(), c.nDepMoveConfirm(), c.kConfirmFreeze(),
                c.rCityDeepMeters(), c.rCityPlateauMeters(), c.tCityDwellSec(),
                c.mCityFixes(), c.kCityExit(), c.dCityExitDeltaMeters(), c.gCitySpanGapSec(),
                c.tCityExitMinSpanSec(), c.tPostBoundaryGuardSec(), c.kConfirmPostBoundary(),
                c.epsMidlineMeters(), c.dOnlineMeters(), c.kOffRouteReacq(), c.minReacqTravelMeters(),
                c.wTurnWindowMaxMeters(), c.turnTauNomSec(), c.turnVClampMs());
    }

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
    void parallelRoadWithinExtendedWindowDoesNotConfirmTurn() {
        CoreConfig cfg = pointW600N3();
        RouteTopology topo = RouteTopology.thereAndBack(G61_1, G61_0);
        MotionFilterCore core = new MotionFilterCore(cfg);
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

        List<GpsFix> parallel = new ArrayList<>();
        for (double s = 300; s <= 2100; s += 60, t += 5) {
            parallel.add(fixOffAxis(G61_0, s, 190.0, 15.0, t));
        }
        List<Step> offSteps = drive(core, topo, parallel);

        int firstOffRoute = offSteps.stream().map(Step::mode).toList().indexOf("OFF_ROUTE");
        assertThat(firstOffRoute)
                .as("параллель 300-600 м в расширенном окне w=600: честный OFF_ROUTE наступает")
                .isPositive();
        assertThat(offSteps.subList(0, firstOffRoute))
                .as("turn-confirm по параллели ОТСУТСТВУЕТ (никакого NEW_TRIP до OFF_ROUTE)")
                .noneMatch(st -> st.mode().equals("NEW_TRIP"));
        assertThat(offSteps.subList(0, firstOffRoute))
                .as("модель не цепляется за опп-ось трекингом по параллели")
                .noneMatch(st -> st.mode().equals("TRACKING"));
    }
}
