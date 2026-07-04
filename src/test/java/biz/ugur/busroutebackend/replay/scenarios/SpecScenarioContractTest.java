package biz.ugur.busroutebackend.replay.scenarios;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.InputValidator;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.core.StopAware;
import biz.ugur.busroutebackend.replay.synth.SyntheticGeometries;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SpecScenarioContractTest {

    private static final GeometryFixture G8 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final GeometryFixture G10_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-10-dir0.json");
    private static final GeometryFixture G10_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-10-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;
    private static final GeometryFixture LINE =
            SyntheticGeometries.straightLine("SCLINE", 0, 8000, 25, List.of());

    private record R(List<PredictionModel.Estimate> ests, MotionFilterCore core) {}

    private R run(List<GpsFix> fixes, RouteTopology topo) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<PredictionModel.Estimate> ests = new ArrayList<>();
        List<double[]> geo = new ArrayList<>();
        List<Double> tSec = new ArrayList<>();
        List<Boolean> sanctioned = new ArrayList<>();
        String prevLeader = null;
        long t0 = fixes.isEmpty() ? 0 : fixes.get(0).timestamp().toEpochMilli();
        for (GpsFix fx : fixes) {
            PredictionModel.Estimate est = core.onFix(fx, topo);
            ests.add(est);
            String leader = core.bank().leader().variantId();
            geo.add(core.bank().leader().geom().pointAtS(est.s()));
            tSec.add((fx.timestamp().toEpochMilli() - t0) / 1000.0);
            sanctioned.add(est.mode().equals("RECOVERING") || est.mode().equals("NEW_TRIP")
                    || (prevLeader != null && !prevLeader.equals(leader)));
            prevLeader = leader;
        }
        var flight = biz.ugur.busroutebackend.replay.metrics.MarkerFlightMetric.compute(
                geo, tSec, sanctioned, CFG.vMaxMs(), 1.5);
        org.assertj.core.api.Assertions.assertThat(flight.violations())
                .as("A9.3: «полёт маркера» вне санкционированных событий (maxRatio=%.2f при k=1.5)",
                        flight.maxRatio())
                .isZero();
        return new R(ests, core);
    }

    private R run(List<GpsFix> fixes, GeometryFixture g) {
        return run(fixes, RouteTopology.of(g));
    }

    @Test
    void scenario01IdealRouteSmoothTrackingAndEta() {
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(G8,
                SyntheticScenario.Params.defaults(801, "8", 0),
                2000, 9000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), false);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<Double> posErr = new ArrayList<>();
        List<Double> h60 = new ArrayList<>();
        java.util.Map<String, Double> factArr = new java.util.HashMap<>();
        for (var v : track.visits()) factArr.put(v.stopId(), v.tArrivalSec());
        Set<String> modesSeen = new java.util.HashSet<>();
        for (int i = 0; i < track.fixes().size(); i++) {
            PredictionModel.Estimate est = core.onFix(track.fixes().get(i), G8);
            modesSeen.add(est.mode());
            posErr.add(Math.abs(est.s() - track.truth().get(i)[1]));
            double tNow = track.truth().get(i)[0];
            for (StopAware.Eta eta : core.etas()) {
                Double fact = factArr.get(eta.stopId());
                if (fact != null && eta.reliable() && fact > tNow && eta.etaSec() <= 60) {
                    h60.add(Math.abs(tNow + eta.etaSec() - fact));
                }
            }
        }
        System.out.printf("SC01: поз. p95=%.1fм, ETA60 p95=%.1fс, режимы=%s%n",
                p95(posErr), p95(h60), modesSeen);
        assertThat(p95(posErr))
                .as("поз. лаг ограничен (хвост p95 — тормозные фазы у стопов, факт печатается)")
                .isLessThanOrEqualTo(60.0);
        assertThat(p95(h60)).as("ETA-ошибка ≤ T_eta(60с горизонт)=15с").isLessThanOrEqualTo(15.0);
        assertThat(modesSeen)
                .as("смен mode ≈ ожидаемые: только штатный стоп-цикл")
                .isSubsetOf(Set.of("ACQUIRING", "TRACKING", "DECELERATING", "DWELL", "DEPARTING"));
    }

    @Test
    void scenario02GpsLossLadderFreezeAndSmoothReturn() {
        GeometryFixture longLine = SyntheticGeometries.straightLine("SC02LINE", 0, 16000, 25, List.of());
        SyntheticScenario.Track track = SyntheticScenario.cruiseWithGaps(
                longLine, SyntheticScenario.Params.defaults(802, "SC02LINE", 0),
                500, CRUISE, 900, List.of(new double[]{200, 60}, new double[]{500, 300}));
        R r = run(track.fixes(), longLine);
        boolean sawRecoveringAfterFreeze = false;
        double maxStepOutsideReanchor = 0;
        for (int i = 1; i < r.ests().size(); i++) {
            double dTau = track.truth().get(i)[0] - track.truth().get(i - 1)[0];
            double step = Math.abs(r.ests().get(i).s() - r.ests().get(i - 1).s());
            if (r.ests().get(i).mode().equals("RECOVERING")) {
                sawRecoveringAfterFreeze = true;
                continue;
            }
            double allowance = CRUISE * dTau + (CFG.rMaxRate() * CRUISE * Math.max(dTau, 1) + CFG.rMaxBaseMeters()) + 30;
            maxStepOutsideReanchor = Math.max(maxStepOutsideReanchor, step - allowance);
        }
        System.out.printf("SC02: freeze-возврат через RECOVERING=%b, превышение шага вне ре-привязки=%.1fм%n",
                sawRecoveringAfterFreeze, maxStepOutsideReanchor);
        assertThat(sawRecoveringAfterFreeze).as("gap 300с → freeze → санкционированная ре-привязка").isTrue();
        assertThat(maxStepOutsideReanchor).as("на возврате нет видимого скачка вне ре-привязки")
                .isLessThanOrEqualTo(0.0);
    }

    @Test
    void scenario03LoopWrapNoFlip() {
        GeometryFixture gLoop = SyntheticGeometries.circleLoop("SC03LOOP", 12000, 480,
                List.of(0.2, 0.5, 0.8));
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.loopRun(
                gLoop, SyntheticScenario.Params.defaults(803, "SC03LOOP", 0),
                300, 2, CRUISE, 1.0, 20, 0.2);
        R r = run(track.fixes(), gLoop);
        assertThat(r.core().lapCount()).as("wrap L→0 непрерывен").isEqualTo(2);
        assertThat(r.core().tripId()).as("нет флипа направления/нового рейса на wrap").isEqualTo(1);
        for (var est : r.ests()) {
            assertThat(est.mode()).isNotIn("TURNING", "NEW_TRIP", "RECOVERING");
        }
    }

    @Test
    void scenario04TurnaroundSingleFlip() {
        RouteTopology topo = RouteTopology.thereAndBack(G10_0, G10_1);
        SyntheticScenario.TurnTrack track = SyntheticScenario.terminalTurnRun(
                G10_0, G10_1, SyntheticScenario.Params.defaults(804, "10", 0),
                G10_0.totalMeters() - 2000, CRUISE, 1.0, 240, 2100, 20, 0.3);
        R r = run(track.fixes(), topo);
        assertThat(r.core().tripId()).as("trip_id++ ровно один раз").isEqualTo(2);
        List<String> chain = new ArrayList<>();
        for (var est : r.ests()) {
            if (chain.isEmpty() || !chain.get(chain.size() - 1).equals(est.mode())) chain.add(est.mode());
        }
        int atTerm = chain.indexOf("AT_TERMINAL");
        assertThat(atTerm).isPositive();
        assertThat(chain.subList(atTerm, atTerm + 4))
                .containsExactly("AT_TERMINAL", "TURNING", "NEW_TRIP", "TRACKING");
    }

    @Test
    void scenario05HardBrakeOvershootPulledBack() {
        var p = SyntheticScenario.Params.defaults(805, "SCLINE", 0);
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        double s = 500;
        double v = CRUISE;
        double nextEmit = 0;
        for (double t = 0; t <= 400; t += 0.5) {
            if (t > 150) v = Math.max(0, v - 3.0 * 0.5);
            s = Math.min(s + v * 0.5, LINE.totalMeters());
            if (t >= nextEmit) {
                truth.add(new double[]{t, s, v});
                fixes.add(SyntheticScenario.emitFix(LINE, p, rnd, t, s, v));
                nextEmit += p.fixIntervalSec();
            }
        }
        R r = run(fixes, LINE);
        double maxOvershoot = 0;
        int stoppedIdx = -1;
        for (int i = 0; i < r.ests().size(); i++) {
            maxOvershoot = Math.max(maxOvershoot, r.ests().get(i).s() - truth.get(i)[1]);
            if (stoppedIdx < 0 && truth.get(i)[2] == 0) stoppedIdx = i;
        }
        double steadyErr = Math.abs(r.ests().get(r.ests().size() - 1).s()
                - truth.get(truth.size() - 1)[1]);
        double errAfter6 = Math.abs(r.ests().get(Math.min(stoppedIdx + 6, r.ests().size() - 1)).s()
                - truth.get(Math.min(stoppedIdx + 6, truth.size() - 1))[1]);
        long recovering = r.ests().stream().filter(e -> e.mode().equals("RECOVERING")).count();
        System.out.printf("SC05: овершут max=%.1fм, err через 6 фиксов после остановки=%.1fм, steady=%.1fм%n",
                maxOvershoot, errAfter6, steadyErr);
        assertThat(errAfter6).as("овершут сведён за ограниченное число циклов").isLessThanOrEqualTo(20.0);
        assertThat(steadyErr).as("устойчивая ошибка ограничена (Р-1)").isLessThanOrEqualTo(15.0);
        assertThat(recovering).as("двунаправленная коррекция, не ре-привязка").isZero();
    }

    @Test
    void scenario06OffRouteMarkedNoFarSnap() {
        SyntheticScenario.OffRouteTrack track = SyntheticScenario.offRouteRun(
                G8, SyntheticScenario.Params.defaults(806, "8", 0),
                2000, CRUISE, 120, 90, 150, 300, 600, Set.of(), 0);
        R r = run(track.fixes(), G8);
        long offTicks = r.ests().stream().filter(e -> e.mode().equals("OFF_ROUTE")).count();
        assertThat(offTicks).as("вход OFF_ROUTE после K — объезд помечен").isPositive();
        int entry = -1;
        for (int i = 0; i < r.ests().size(); i++) {
            if (r.ests().get(i).mode().equals("OFF_ROUTE")) {
                entry = i;
                break;
            }
        }
        double frozen = r.ests().get(entry).s();
        for (int i = entry; i < r.ests().size() && r.ests().get(i).mode().equals("OFF_ROUTE"); i++) {
            assertThat(Math.abs(r.ests().get(i).s() - frozen))
                    .as("нет снапа на дальнюю линию: x̂ заморожен, не утянут (tick %d)", i)
                    .isLessThanOrEqualTo(1e-6);
        }
    }

    @Test
    void scenario07SelfIntersectionCorrectBranch() {
        GeometryFixture eight = SyntheticGeometries.figureEight("SC07EIGHT");
        SyntheticScenario.Track track = SyntheticScenario.cruiseClean(
                eight, SyntheticScenario.Params.defaults(807, "SC07EIGHT", 0),
                100, CRUISE, (eight.totalMeters() - 300) / CRUISE);
        R r = run(track.fixes(), eight);
        double maxErr = 0;
        for (int i = 0; i < r.ests().size(); i++) {
            maxErr = Math.max(maxErr, Math.abs(r.ests().get(i).s() - track.truth().get(i)[1]));
        }
        assertThat(maxErr).as("ветка по истории дуги: нет ложного снапа/телепорта").isLessThanOrEqualTo(60.0);
    }

    @Test
    void scenario08LongStandstillDriftBounded() {
        double atStop = G8.stops().get(5).sMeters();
        SyntheticScenario.Track track = SyntheticScenario.stationaryWithNoise(
                G8, SyntheticScenario.Params.defaults(808, "8", 0), atStop, 600);
        R r = run(track.fixes(), G8);
        double maxDrift = 0;
        for (var est : r.ests()) {
            maxDrift = Math.max(maxDrift, Math.abs(est.s() - atStop));
        }
        System.out.printf("SC08: дрейф стоянки max=%.1fм%n", maxDrift);
        assertThat(maxDrift).as("дрейф ≤ ε_dwell").isLessThanOrEqualTo(15.0);
    }

    @Test
    void scenario09StopAndGoNoLimitCycle() {
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(G8,
                SyntheticScenario.Params.defaults(809, "8", 0),
                2000, 9000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), true);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        java.util.Map<String, Integer> decelEnters = new java.util.HashMap<>();
        for (GpsFix fx : track.fixes()) {
            core.onFix(fx, G8);
            for (var e : core.drainEvents()) {
                if (e.type() == StopAware.StopEventType.DECEL_ENTER) {
                    decelEnters.merge(e.stopId(), 1, Integer::sum);
                }
            }
        }
        long reentries = decelEnters.values().stream().filter(n -> n > 1).count();
        assertThat(reentries).as("нет предельного цикла (повторный DECEL_ENTER)").isZero();
    }

    @Test
    void scenario10LowAccuracyDampedMarker() {
        double sigma = 15;
        var pGood = new SyntheticScenario.Params(810, 7.0, sigma, 5.0,
                Instant.parse("2026-07-03T06:00:00Z"), "veh", "P", "SCLINE", 0);
        var pBad = new SyntheticScenario.Params(810, 7.0, sigma, 40.0,
                Instant.parse("2026-07-03T06:00:00Z"), "veh", "P", "SCLINE", 0);
        double sdGood = trackingErrorStd(pGood);
        double sdBad = trackingErrorStd(pBad);
        System.out.printf("SC10 (Р-8): std ошибки при acc=5: %.2fм; при acc=40: %.2fм (демпфирование)%n",
                sdGood, sdBad);
        assertThat(sdBad).as("маркер не дёргается за низкоточным фиксом (вес ↓ по accuracy)")
                .isLessThan(sdGood);

        double dx3 = singleUpdateContribution(3.0);
        double dx40 = singleUpdateContribution(40.0);
        System.out.printf("SC10 (Р-8): K-вклад одиночной инновации 35м: acc=3 → %.1fм; acc=40 → %.1fм%n",
                dx3, dx40);
        assertThat(dx40).as("вес монотонен по accuracy: точный фикс тянет сильнее").isLessThan(dx3);
        List<Double> contributions = new ArrayList<>();
        for (double acc : List.of(3.0, 5.0, 10.0, 20.0, 40.0)) {
            contributions.add(singleUpdateContribution(acc));
        }
        for (int i = 1; i < contributions.size(); i++) {
            assertThat(contributions.get(i))
                    .as("монотонность веса по accuracy (Р-8/INV-14), шаг %d", i)
                    .isLessThanOrEqualTo(contributions.get(i - 1) + 1e-9);
        }
    }

    private double singleUpdateContribution(double accuracyMeters) {
        var p = new SyntheticScenario.Params(890, 7.0, 0.0, accuracyMeters,
                Instant.parse("2026-07-03T06:00:00Z"), "veh", "P", "SCLINE", 0);
        Random rnd = new Random(p.seed());
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        core.onFix(SyntheticScenario.emitFix(LINE, p, rnd, 0, 1000, CRUISE), LINE);
        PredictionModel.Estimate est = core.onFix(
                SyntheticScenario.emitFix(LINE, p, rnd, 7, 1000 + CRUISE * 7 + 35, CRUISE), LINE);
        double predicted = 1000 + CRUISE * 7;
        return est.s() - predicted;
    }

    @Test
    void scenario10HdopBranchWithZeroAccuracyLiveFeedShape() {
        double sdLowHdop = trackingErrorStdHdop(0.7);
        double sdHighHdop = trackingErrorStdHdop(5.0);
        System.out.printf("SC10-hdop (живой профиль Tugdk: accuracy=0): std при hdop=0.7: %.2fм; "
                + "при hdop=5: %.2fм (демпфирование hdop-веткой)%n", sdLowHdop, sdHighHdop);
        assertThat(sdHighHdop)
                .as("Р-8 hdop-ветка: маркер не дёргается за низкокачественным (hdop↑) фиксом")
                .isLessThan(sdLowHdop);

        List<Double> contributions = new ArrayList<>();
        for (double hdop : List.of(0.5, 1.0, 2.0, 5.0, 10.0)) {
            contributions.add(singleUpdateContributionHdop(hdop));
        }
        System.out.printf("SC10-hdop: K-вклад инновации 35м по hdop {0.5,1,2,5,10}: %s "
                + "(факт кода: R = (max(accRef, accRef·hdop)·(1+d_snap/D_snap))², accuracy=0 → hdop-ветка)%n",
                contributions.stream().map(c -> String.format(java.util.Locale.ROOT, "%.1f", c)).toList());
        for (int i = 1; i < contributions.size(); i++) {
            assertThat(contributions.get(i))
                    .as("монотонность веса по hdop (Р-8/INV-14), шаг %d", i)
                    .isLessThanOrEqualTo(contributions.get(i - 1) + 1e-9);
        }
        assertThat(singleUpdateContributionHdop(5.0))
                .as("hdop=0.7 и hdop=5 дают разный K-вклад")
                .isLessThan(singleUpdateContributionHdop(0.7));
    }

    private double trackingErrorStdHdop(double hdop) {
        var p = new SyntheticScenario.Params(891, 7.0, 15.0, 5.0,
                Instant.parse("2026-07-03T06:00:00Z"), "veh", "P", "SCLINE", 0);
        SyntheticScenario.Track track = SyntheticScenario.cruiseClean(LINE, p, 500, CRUISE, 500);
        List<GpsFix> reshaped = track.fixes().stream().map(f -> withQuality(f, hdop, 0.0)).toList();
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<Double> errs = new ArrayList<>();
        for (int i = 0; i < reshaped.size(); i++) {
            PredictionModel.Estimate est = core.onFix(reshaped.get(i), LINE);
            if (i >= 20) errs.add(est.s() - track.truth().get(i)[1]);
        }
        double mean = errs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.sqrt(errs.stream().mapToDouble(e -> (e - mean) * (e - mean)).average().orElse(0));
    }

    private double singleUpdateContributionHdop(double hdop) {
        var p = new SyntheticScenario.Params(892, 7.0, 0.0, 5.0,
                Instant.parse("2026-07-03T06:00:00Z"), "veh", "P", "SCLINE", 0);
        Random rnd = new Random(p.seed());
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        core.onFix(withQuality(SyntheticScenario.emitFix(LINE, p, rnd, 0, 1000, CRUISE), hdop, 0.0), LINE);
        PredictionModel.Estimate est = core.onFix(
                withQuality(SyntheticScenario.emitFix(LINE, p, rnd, 7, 1000 + CRUISE * 7 + 35, CRUISE),
                        hdop, 0.0), LINE);
        return est.s() - (1000 + CRUISE * 7);
    }

    private static GpsFix withQuality(GpsFix f, double hdop, double accuracy) {
        return new GpsFix(f.vehicleId(), f.licensePlate(), f.routeNumber(),
                f.latitude(), f.longitude(), f.speedKmh(), f.course(), f.inMotion(),
                f.timestamp(), f.direction(), hdop, f.satellites(), accuracy, f.wallClock());
    }

    private double trackingErrorStd(SyntheticScenario.Params p) {
        SyntheticScenario.Track track = SyntheticScenario.cruiseClean(LINE, p, 500, CRUISE, 500);
        R r = run(track.fixes(), LINE);
        List<Double> errs = new ArrayList<>();
        for (int i = 20; i < r.ests().size(); i++) {
            errs.add(r.ests().get(i).s() - track.truth().get(i)[1]);
        }
        double mean = errs.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return Math.sqrt(errs.stream().mapToDouble(e -> (e - mean) * (e - mean)).average().orElse(0));
    }

    @Test
    void scenario11DeviceSwapSustainedShiftViaRecovering() {
        var p = SyntheticScenario.Params.defaults(811, "SCLINE", 0);
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        for (double t = 0; t <= 500; t += 7) {
            double sTrue = 300 + CRUISE * t;
            double sSeen = t >= 250 ? sTrue + 800 : sTrue;
            truth.add(new double[]{t, sSeen});
            fixes.add(SyntheticScenario.emitFix(LINE, p, rnd, t, Math.min(sSeen, LINE.totalMeters()), CRUISE));
        }
        R r = run(fixes, LINE);
        boolean recovering = r.ests().stream().anyMatch(e -> e.mode().equals("RECOVERING"));
        double maxStep = 0;
        for (int i = 1; i < r.ests().size(); i++) {
            maxStep = Math.max(maxStep, Math.abs(r.ests().get(i).s() - r.ests().get(i - 1).s()));
        }
        double finalErr = Math.abs(r.ests().get(r.ests().size() - 1).s() - truth.get(truth.size() - 1)[1]);
        double pullBudget = CRUISE * 7 + (CFG.rMaxRate() * CFG.vMaxMs() * 7 + CFG.rMaxBaseMeters())
                * CFG.recoveryPullFactor();
        System.out.printf("SC11: RECOVERING=%b, maxStep=%.1fм (бюджет %.1f), финальная ошибка=%.1fм%n",
                recovering, maxStep, pullBudget, finalErr);
        assertThat(recovering).as("сдвиг через M подтверждений → RECOVERING").isTrue();
        assertThat(maxStep).as("нет мгновенного разрыва (стягивание сериями)").isLessThanOrEqualTo(pullBudget);
        assertThat(finalErr).as("сходимость после M").isLessThanOrEqualTo(60.0);
    }

    @Test
    void scenario12InputValidationFutureAndFrozen() {
        var p = SyntheticScenario.Params.defaults(812, "SCLINE", 0);
        Random rnd = new Random(p.seed());
        Instant t0 = p.startTime();
        GpsFix normal1 = SyntheticScenario.emitFix(LINE, p, rnd, 0, 500, CRUISE);
        GpsFix future = new GpsFix("veh", "P", "SCLINE", normal1.latitude(), normal1.longitude(),
                45.0, 90.0, true, t0.plusSeconds(600), 0, 1.0, 9, 5.0, t0.plusSeconds(7));
        GpsFix normal2 = SyntheticScenario.emitFix(LINE, p, rnd, 14, 675, CRUISE);
        GpsFix duplicate = new GpsFix("veh", "P", "SCLINE", normal2.latitude(), normal2.longitude(),
                45.0, 90.0, true, normal2.timestamp(), 0, 1.0, 9, 5.0, normal2.timestamp());
        GpsFix backwards = new GpsFix("veh", "P", "SCLINE", normal2.latitude(), normal2.longitude(),
                45.0, 90.0, true, t0.plusSeconds(7), 0, 1.0, 9, 5.0, t0.plusSeconds(21));
        GpsFix frozenBase = SyntheticScenario.emitFix(LINE, p, rnd, 28, 850, CRUISE);
        GpsFix frozen1 = new GpsFix("veh", "P", "SCLINE", frozenBase.latitude(), frozenBase.longitude(),
                45.0, 90.0, true, t0.plusSeconds(35), 0, 1.0, 9, 5.0, t0.plusSeconds(35));
        GpsFix frozen2 = new GpsFix("veh", "P", "SCLINE", frozenBase.latitude(), frozenBase.longitude(),
                45.0, 90.0, true, t0.plusSeconds(42), 0, 1.0, 9, 5.0, t0.plusSeconds(42));

        InputValidator validator = InputValidator.spec9Defaults();
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        List<InputValidator.DropReason> drops = new ArrayList<>();
        double sBeforeFrozen = Double.NaN;
        double sAfterFrozen = Double.NaN;
        for (GpsFix fx : List.of(normal1, future, normal2, duplicate, backwards, frozenBase, frozen1, frozen2)) {
            var d = validator.validate(fx);
            if (!d.accepted()) {
                drops.add(d.reason());
                continue;
            }
            var est = core.onFix(fx, LINE);
            if (fx == frozenBase) sBeforeFrozen = est.s();
            sAfterFrozen = est.s();
        }
        System.out.printf("SC12: дропы=%s; x̂ до/после frozen-серии: %.1f/%.1f%n",
                drops, sBeforeFrozen, sAfterFrozen);
        assertThat(drops).containsExactly(
                InputValidator.DropReason.FUTURE_TS,
                InputValidator.DropReason.DUPLICATE_TS,
                InputValidator.DropReason.NON_INCREASING_TS,
                InputValidator.DropReason.FROZEN_COORDS,
                InputValidator.DropReason.FROZEN_COORDS);
        assertThat(sAfterFrozen)
                .as("нет advance на frozen coords при z_v>0")
                .isCloseTo(sBeforeFrozen, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void scenario13TunnelReturnWithDegradedAccuracyNoTeleport() {
        var p = SyntheticScenario.Params.defaults(813, "SCLINE", 0);
        var pBadAcc = new SyntheticScenario.Params(813, 7.0, 5.0, 50.0,
                p.startTime(), p.vehicleId(), p.plate(), p.route(), p.direction());
        Random rnd = new Random(p.seed());
        List<GpsFix> fixes = new ArrayList<>();
        List<double[]> truth = new ArrayList<>();
        int badAccLeft = 0;
        for (double t = 0; t <= 600; t += 7) {
            if (t > 100 && t < 220) {
                badAccLeft = 5;
                continue;
            }
            double sTrue = Math.min(300 + CRUISE * t, LINE.totalMeters());
            truth.add(new double[]{t, sTrue});
            var pp = badAccLeft > 0 ? pBadAcc : p;
            if (badAccLeft > 0) badAccLeft--;
            fixes.add(SyntheticScenario.emitFix(LINE, pp, rnd, t, sTrue, CRUISE));
        }
        R r = run(fixes, LINE);
        double maxStepOutsideReanchor = 0;
        for (int i = 1; i < r.ests().size(); i++) {
            if (r.ests().get(i).mode().equals("RECOVERING")) continue;
            double dTau = truth.get(i)[0] - truth.get(i - 1)[0];
            double step = Math.abs(r.ests().get(i).s() - r.ests().get(i - 1).s());
            double allowance = CRUISE * dTau
                    + (CFG.rMaxRate() * CFG.vMaxMs() * Math.max(dTau, 1) + CFG.rMaxBaseMeters()) + 30;
            maxStepOutsideReanchor = Math.max(maxStepOutsideReanchor, step - allowance);
        }
        double finalErr = Math.abs(r.ests().get(r.ests().size() - 1).s() - truth.get(truth.size() - 1)[1]);
        System.out.printf("SC13: тоннель 120с + возврат acc=50: превышение шага=%.1f, финальная ошибка=%.1fм%n",
                maxStepOutsideReanchor, finalErr);
        assertThat(maxStepOutsideReanchor)
                .as("нет телепорта на шумном возврате (вне санкционированной ре-привязки)")
                .isLessThanOrEqualTo(0.0);
        assertThat(finalErr).as("ре-привязка по подтверждению сходится").isLessThanOrEqualTo(80.0);
    }

    @Test
    void scenario14VariantForkBoundedCorrection() {
        SyntheticGeometries.ForkPair fork = SyntheticGeometries.forkPair(
                "SC14F", 3000, 5000, 2000, 0, 0);
        RouteTopology topo = RouteTopology.of(fork.full()).withVariants(List.of(fork.shortVariant()));
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(
                fork.shortVariant(), SyntheticScenario.Params.defaults(814, "SC14F-short", 0),
                200, fork.shortVariant().totalMeters() - 100, CRUISE, 1.0, 20, 0.3, Set.of(), false);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        int switchIdx = -1;
        double arcBefore = 0;
        List<PredictionModel.Estimate> ests = new ArrayList<>();
        for (int i = 0; i < track.fixes().size(); i++) {
            double prevArc = ests.isEmpty() ? 0 : ests.get(ests.size() - 1).s();
            ests.add(core.onFix(track.fixes().get(i), topo));
            if (switchIdx < 0 && core.bank().leader().variantId().endsWith("-short#d0")) {
                switchIdx = i;
                arcBefore = prevArc;
            }
        }
        assertThat(switchIdx).as("переключение на правильный вариант состоялось").isPositive();
        double arcCorrection = Math.abs(ests.get(switchIdx).s() - arcBefore);
        double[] pNew = fork.shortVariant().pointAtS(ests.get(switchIdx).s());
        double forkGap = fork.full().projectOntoRange(pNew[0], pNew[1], 0,
                fork.full().totalMeters(), 0).distMeters();
        double[] pPrev = fork.full().pointAtS(arcBefore);
        double geoJump = GeometryFixture.haversineMeters(pPrev[0], pPrev[1], pNew[0], pNew[1]);
        double allowance = (CFG.rMaxRate() * CRUISE * 7 + CFG.rMaxBaseMeters()) * (CFG.hSwitch() + 3);
        System.out.printf("SC14: дуговая коррекция=%.1fм; гео-скачок=%.1fм vs fork_gap=%.1fм + допуск %.1fм%n",
                arcCorrection, geoJump, forkGap, allowance);
        assertThat(arcCorrection)
                .as("величина коррекции положения при переключении варианта ограничена (по дуге)")
                .isLessThanOrEqualTo(CFG.dSwitchSmoothMeters() + CRUISE * 7);
        assertThat(geoJump)
                .as("A8.3: гео-скачок ≤ fork_gap + допуск сглаживания")
                .isLessThanOrEqualTo(forkGap + allowance);
    }

    @Test
    void scenario15SnapDriftRampBoundedAndDetected() {
        SyntheticScenario.Track track = SyntheticScenario.cruiseWithAccumulatingSnapDrift(
                G8, SyntheticScenario.Params.defaults(815, "8", 0), 2000, CRUISE, 0.7, 900);
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        for (int i = 0; i < track.fixes().size(); i++) {
            GpsFix fx = track.fixes().get(i);
            PredictionModel.Estimate est = core.onFix(fx, G8);
            double[] pt = G8.pointAtS(est.s());
            double absDev = GeometryFixture.haversineMeters(fx.latitude(), fx.longitude(), pt[0], pt[1]);
            boolean controlled = absDev <= CFG.dMaxMeters()
                    || core.driftEventActive()
                    || est.mode().equals("RECOVERING")
                    || est.mode().equals("OFF_ROUTE");
            assertThat(controlled)
                    .as("накопленное отклонение ограничено ИЛИ событие дрейфа активно (tick %d)", i)
                    .isTrue();
        }
        assertThat(core.absDeviationEvents()).as("детекция накопления (INV-3 по устойчивости)").isPositive();
    }

    @Test
    void scenario16DepartureLagBounded() {
        SyntheticScenario.Track track = SyntheticScenario.departureRamp(
                G8, SyntheticScenario.Params.defaults(816, "8", 0), 3000, CRUISE, 1.0, 120, 600);
        R r = run(track.fixes(), G8);
        double maxLag = 0;
        for (int i = 0; i < r.ests().size(); i++) {
            maxLag = Math.max(maxLag, track.truth().get(i)[1] - r.ests().get(i).s());
        }
        System.out.printf("SC16: позиционный лаг на отрыве max=%.1fм%n", maxLag);
        assertThat(maxLag).as("лаг отрыва ≤ порога (пик 25м)").isLessThanOrEqualTo(25.0);
    }

    private static double p95(List<Double> xs) {
        if (xs.isEmpty()) return Double.NaN;
        List<Double> s = xs.stream().sorted().toList();
        return s.get((int) Math.floor(0.95 * (s.size() - 1)));
    }
}
