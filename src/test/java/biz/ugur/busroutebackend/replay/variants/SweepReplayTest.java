package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class SweepReplayTest {

    private static CoreConfig sweepConfig() {
        CoreConfig c = CoreConfig.defaults();
        int nTurnConfirm = Integer.getInteger("sweep.nTurnConfirm", c.nTurnConfirm());
        double dTurnConfirmMeters = Double.parseDouble(
                System.getProperty("sweep.dTurnConfirmMeters", String.valueOf(c.dTurnConfirmMeters())));
        double wTurnWindowMeters = Double.parseDouble(
                System.getProperty("sweep.wTurnWindowMeters", String.valueOf(c.wTurnWindowMeters())));
        int nTurnConfirmTerm = Integer.getInteger("sweep.nTurnConfirmTerm", c.nTurnConfirmTerm());
        double dTurnConfirmTermMeters = Double.parseDouble(
                System.getProperty("sweep.dTurnConfirmTermMeters", String.valueOf(c.dTurnConfirmTermMeters())));
        return new CoreConfig(c.dtSec(), c.w0Meters(), c.kWindowPerSpeed(), c.sigmaMeasDefaultMeters(),
                c.accuracyRefMeters(), c.dSnapMeters(), c.dMaxMeters(), c.gammaGate(),
                c.qPos(), c.qVel(), c.pInitPos(), c.pInitVel(), c.rMaxRate(), c.rMaxBaseMeters(),
                c.weakZvWeight(), c.nPersist(), c.mReanchor(), c.tLostSec(), c.tMaxSec(),
                c.vTargetMs(), c.aDepMs2(), c.aMaxMs2(), c.vMaxMs(), c.dReanchorMeters(),
                c.recoveryPullFactor(), c.vStopKmh(), c.vMoveKmh(), c.hStop(), c.hDep(), c.hDec(),
                c.dwellMinSec(), c.dDecelMeters(), c.epsArrMeters(), c.epsStopMeters(),
                c.epsTermMeters(), c.dwellExpectedSec(), c.dwellMaxSec(), nTurnConfirm,
                dTurnConfirmMeters, c.unpinWindowTicks(), c.kTurnRevert(), wTurnWindowMeters,
                c.historyNMin(), c.kOffRoute(), c.mOffRouteExit(),
                c.scoreLambda(), c.scoreRejectPenalty(), c.scoreProgressBonus(),
                c.sSwitch(), c.hSwitch(), c.dSwitchSmoothMeters(), c.maxHypotheses(),
                c.rHdopEnabled(), c.rHdopAMeters(), c.rHdopBMetersPerHdop(),
                c.gateNisThreshold(), c.qScale(),
                c.epsCloseTailMeters(), nTurnConfirmTerm, dTurnConfirmTermMeters,
                c.kTermMissOffRoute(), c.nDepMoveConfirm(), c.kConfirmFreeze(),
                c.rCityDeepMeters(), c.rCityPlateauMeters(), c.tCityDwellSec(),
                c.mCityFixes(), c.kCityExit(), c.dCityExitDeltaMeters(), c.gCitySpanGapSec(),
                c.tCityExitMinSpanSec(), c.tPostBoundaryGuardSec(), c.kConfirmPostBoundary(),
                c.epsMidlineMeters(), c.dOnlineMeters(), c.kOffRouteReacq(), c.minReacqTravelMeters(),
                c.wTurnWindowMaxMeters(), c.turnTauNomSec(), c.turnVTargetMs(), c.dTermEscapeMeters(), c.dDirSwitchRunMeters(), c.tDirFlapGuardSec(), c.tTurnLostSec());
    }

    @Test
    @EnabledIfSystemProperty(named = "sweep.corpus", matches = ".+")
    void replayCorpusWithTurnKnobOverrides() throws Exception {
        CoreConfig cfg = sweepConfig();
        Path corpus = Path.of(System.getProperty("sweep.corpus"));
        Path dumpPath = Path.of(System.getProperty("sweep.dump"));
        RouteTopology base = RouteTopology
                .thereAndBack(Variant61FixturesTest.FULL_0, Variant61FixturesTest.FULL_1)
                .withVariants(List.of(Variant61FixturesTest.gokje0().shortVariant(),
                        Variant61FixturesTest.gokjeTail1().shortVariant()));
        String zone = System.getProperty("sweep.cityzone", "");
        final RouteTopology topoTemplate;
        if (!zone.isBlank()) {
            String[] p = zone.split(",");
            topoTemplate = base.withCityZone(new RouteTopology.CityZone(
                    Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]), Double.parseDouble(p[3])));
        } else {
            topoTemplate = base;
        }
        List<Episode> episodes = CorpusLoader.load(corpus, 1e9, 1);
        Map<String, List<GpsFix>> byVehicle = new TreeMap<>();
        for (Episode ep : episodes) {
            if (!ep.routeNumber().equals("61")) continue;
            byVehicle.computeIfAbsent(ep.vehicleId(), k -> new ArrayList<>()).addAll(ep.fixes());
        }
        long totalTicks = 0;
        try (BufferedWriter w = Files.newBufferedWriter(dumpPath)) {
            w.write("vid8|ts|mode|leader|s|dir|tripId|partial\n");
            for (Map.Entry<String, List<GpsFix>> e : byVehicle.entrySet()) {
                List<GpsFix> fixes = e.getValue();
                fixes.sort(Comparator.comparing(GpsFix::timestamp));
                MotionFilterCore core = new MotionFilterCore(cfg);
                core.reset();
                String vid8 = e.getKey().substring(0, 8);
                for (GpsFix fx : fixes) {
                    var est = core.onFix(fx, topoTemplate);
                    w.write(String.format(Locale.ROOT, "%s|%s|%s|%s|%.1f|%d|%d|%s%n",
                            vid8, fx.timestamp(), est.mode(),
                            core.bank().leader().variantId(), est.s(), core.direction(),
                            core.tripId(), core.currentTripPartial() ? "y" : "n"));
                    totalTicks++;
                }
            }
        }
        System.out.printf("sweep: бортов=%d тиков=%d дамп=%s cfg{wTurnWindow=%.0f nTurn=%d dTurn=%.0f "
                        + "nTurnTerm=%d dTurnTerm=%.0f}%n",
                byVehicle.size(), totalTicks, dumpPath, cfg.wTurnWindowMeters(), cfg.nTurnConfirm(),
                cfg.dTurnConfirmMeters(), cfg.nTurnConfirmTerm(), cfg.dTurnConfirmTermMeters());
        assertThat(totalTicks).isPositive();
    }
}
