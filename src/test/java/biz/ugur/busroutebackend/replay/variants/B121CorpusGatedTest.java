package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.InputValidator;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class B121CorpusGatedTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double T_TERM = 150.0;
    private static final double B_TERM = 0.5;
    private static final double EXIT_JUMP_CAP_M = 1050.0 + 200.0;
    private static final double FOLD_FROM_M = 18150.0;
    private static final double FOLD_TO_M = 21250.0;

    private record ApexWindow(String veh8, Instant center) {}

    private static final List<ApexWindow> GOKJE_APEX_WINDOWS = List.of(
            new ApexWindow("b6be7191", Instant.parse("2026-07-05T04:14:00Z")),
            new ApexWindow("b6be7191", Instant.parse("2026-07-06T04:15:00Z")),
            new ApexWindow("e84e525c", Instant.parse("2026-07-05T03:05:00Z")),
            new ApexWindow("e84e525c", Instant.parse("2026-07-05T08:05:00Z")),
            new ApexWindow("e84e525c", Instant.parse("2026-07-06T05:27:00Z")),
            new ApexWindow("e84e525c", Instant.parse("2026-07-07T09:28:00Z")),
            new ApexWindow("906cba19", Instant.parse("2026-07-06T01:18:00Z")),
            new ApexWindow("9351964e", Instant.parse("2026-07-05T04:39:00Z")),
            new ApexWindow("2239d7c4", Instant.parse("2026-07-06T13:50:00Z")),
            new ApexWindow("c24add2d", Instant.parse("2026-07-07T13:10:00Z")));

    private record NewTripEvent(String veh8, long epochMs, String leader) {}

    private record RunOut(String sha, List<NewTripEvent> newTrips, long flightViolInFold,
                          long flightViolOutFold, double flightMaxInFold, double flightMaxOutFold,
                          double nisMeanShared, int nisN, double nisMeanPinned, int nisPinnedN,
                          long bTermTicks, long vImplOver100InWindows, int windowsWithData,
                          int windowsLeaderGokjeAtApex, List<Double> exitJumps) {}

    private RunOut run(Path corpusDir, boolean withPair) {
        StringBuilder nisDump = new StringBuilder();
        RouteTopology topo = withPair
                ? RouteTopology.thereAndBack(Variant61FixturesTest.FULL_0, Variant61FixturesTest.FULL_1)
                    .withVariants(List.of(Variant61FixturesTest.gokje0().shortVariant(),
                            Variant61FixturesTest.gokjeTail1().shortVariant()))
                : RouteTopology.thereAndBack(Variant61FixturesTest.FULL_0, Variant61FixturesTest.FULL_1);
        StringBuilder registry = new StringBuilder();
        List<NewTripEvent> newTrips = new ArrayList<>();
        long violIn = 0;
        long violOut = 0;
        double maxIn = 0;
        double maxOut = 0;
        double nisSum = 0;
        int nisN = 0;
        double nisPinSum = 0;
        int nisPinN = 0;
        long bTerm = 0;
        long vImplWin = 0;
        boolean[] winHasData = new boolean[GOKJE_APEX_WINDOWS.size()];
        boolean[] winLeaderOk = new boolean[GOKJE_APEX_WINDOWS.size()];
        long[] winBestDt = new long[GOKJE_APEX_WINDOWS.size()];
        java.util.Arrays.fill(winBestDt, Long.MAX_VALUE);
        List<Double> exitJumps = new ArrayList<>();

        for (Episode ep : CorpusLoader.load(corpusDir, CFG.tMaxSec(), 10)) {
            if (!ep.routeNumber().equals("61")) continue;
            String veh8 = ep.vehicleId().substring(0, 8);
            MotionFilterCore core = new MotionFilterCore(CFG);
            core.reset();
            core.bank().configureTerminalSignal(T_TERM, B_TERM);
            InputValidator validator = InputValidator.spec9Defaults();
            String prevLeader = null;
            String prevMode = "";
            double[] prevGeo = null;
            double prevT = 0;
            long t0 = ep.fixes().get(0).timestamp().toEpochMilli();
            for (GpsFix fx : ep.fixes()) {
                if (!validator.validate(fx).accepted()) continue;
                PredictionModel.Estimate est = core.onFix(fx, topo);
                String leader = core.bank().leader().variantId();
                double[] geo = core.bank().leader().geom().pointAtS(est.s());
                double tSec = (fx.timestamp().toEpochMilli() - t0) / 1000.0;
                boolean sanctioned = est.mode().equals("RECOVERING") || est.mode().equals("NEW_TRIP")
                        || (prevLeader != null && !prevLeader.equals(leader));

                registry.append(veh8).append('|').append(fx.timestamp().toEpochMilli()).append('|')
                        .append(est.mode()).append('|').append(leader).append('|')
                        .append(String.format(Locale.ROOT, "%.1f", est.s())).append('\n');

                if (est.mode().equals("NEW_TRIP") && !prevMode.equals("NEW_TRIP")) {
                    newTrips.add(new NewTripEvent(veh8, fx.timestamp().toEpochMilli(), leader));
                }

                if (prevGeo != null) {
                    double dt = Math.max(tSec - prevT, 1.0);
                    double dist = GeometryFixture.haversineMeters(prevGeo[0], prevGeo[1], geo[0], geo[1]);
                    double ratio = dist / (dt * CFG.vMaxMs());
                    boolean inFold = leader.equals("61#d0") && est.s() >= FOLD_FROM_M && est.s() <= FOLD_TO_M;
                    if (!sanctioned) {
                        if (inFold) {
                            maxIn = Math.max(maxIn, ratio);
                            if (ratio > 1.5) violIn++;
                        } else {
                            maxOut = Math.max(maxOut, ratio);
                            if (ratio > 1.5) violOut++;
                        }
                    }
                    if (prevLeader != null && prevLeader.equals("61-gokje#d0")
                            && !leader.equals("61-gokje#d0") && withPair) {
                        exitJumps.add(dist);
                        System.out.printf(Locale.ROOT,
                                "exit-скачок: %s @%s %s→%s |dp|=%.1fм%n",
                                veh8, fx.timestamp(), prevLeader, leader, dist);
                    }
                    for (int w = 0; w < GOKJE_APEX_WINDOWS.size(); w++) {
                        ApexWindow win = GOKJE_APEX_WINDOWS.get(w);
                        long dtw = Math.abs(fx.timestamp().toEpochMilli() - win.center().toEpochMilli());
                        if (!veh8.equals(win.veh8()) || dtw > 180_000) continue;
                        winHasData[w] = true;
                        double vImpl = dist / dt * 3.6;
                        if (!sanctioned && vImpl > 100) vImplWin++;
                        if (dtw < winBestDt[w]) {
                            winBestDt[w] = dtw;
                            winLeaderOk[w] = leader.startsWith("61-gokje");
                        }
                    }
                }
                if (core.lastUpdateAccepted() && !Double.isNaN(core.lastInnovation())
                        && core.lastInnovationVariance() > 0) {
                    double nis = core.lastInnovation() * core.lastInnovation() / core.lastInnovationVariance();
                    boolean pinned = false;
                    for (var h : core.bank().hypotheses()) {
                        if (h == core.bank().leader() && h.pinnedAtVariantTerminal()) pinned = true;
                    }
                    if (pinned) {
                        nisPinSum += nis;
                        nisPinN++;
                    } else {
                        nisSum += nis;
                        nisN++;
                    }
                    if (System.getProperty("b12_1.dumpRegistry") != null) {
                        nisDump.append(veh8).append('|').append(fx.timestamp().toEpochMilli())
                                .append('|').append(pinned ? "PIN" : "STD").append('|')
                                .append(String.format(Locale.ROOT, "%.6f", nis)).append('\n');
                    }
                }
                prevLeader = leader;
                prevMode = est.mode();
                prevGeo = geo;
                prevT = tSec;
            }
            bTerm += core.bank().bTermActiveTicks();
        }
        String sha;
        try {
            sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(registry.toString().getBytes(StandardCharsets.UTF_8))).substring(0, 12);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String dumpBase = System.getProperty("b12_1.dumpRegistry");
        if (dumpBase != null) {
            String suffix = withPair ? ".gprime" : ".base";
            try {
                Files.writeString(Path.of(dumpBase + suffix), registry.toString());
                Files.writeString(Path.of(dumpBase + suffix + ".nis"), nisDump.toString());
                System.out.printf("registry-дамп (%s, после SHA %s): %s%n", suffix, sha, dumpBase + suffix);
            } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        int hasData = 0;
        int leaderOk = 0;
        for (int w = 0; w < winHasData.length; w++) {
            if (winHasData[w]) hasData++;
            if (winHasData[w] && winLeaderOk[w]) leaderOk++;
            System.out.printf(Locale.ROOT, "окно[%d] %s @%s: данные=%s bestDt=%s leaderOk=%s%n",
                    w, GOKJE_APEX_WINDOWS.get(w).veh8(), GOKJE_APEX_WINDOWS.get(w).center(),
                    winHasData[w], winBestDt[w] == Long.MAX_VALUE ? "—" : winBestDt[w] / 1000 + "с",
                    winLeaderOk[w]);
        }
        return new RunOut(sha, newTrips, violIn, violOut, maxIn, maxOut,
                nisN > 0 ? nisSum / nisN : Double.NaN, nisN,
                nisPinN > 0 ? nisPinSum / nisPinN : Double.NaN, nisPinN,
                bTerm, vImplWin, hasData, leaderOk, exitJumps);
    }

    @Test
    @EnabledIfSystemProperty(named = "b12_1.final", matches = "true")
    void corpusBase61VsGPrime() throws Exception {
        Path corpusDir = Path.of(System.getProperty("corpus.dir"));
        RunOut base = run(corpusDir, false);
        RunOut g = run(corpusDir, true);

        StringBuilder md = new StringBuilder();
        md.append("# B12.1 П.6 — корпусная проверка 61: base61 vs G′\n\n");
        md.append(String.format(Locale.ROOT, "Корпус: %s (манифест B12, Д-2а). SHA реестров: base61 = %s, G′ = %s.%n%n",
                corpusDir, base.sha(), g.sha()));
        md.append(String.format(Locale.ROOT,
                "| Метрика | base61 | G′ |%n|---|---|---|%n"
                        + "| NEW_TRIP-спеллов | %d | %d |%n"
                        + "| полёт: нарушения в самоскладке [18 150;21 250] | %d (max %.2f) | %d (max %.2f) |%n"
                        + "| полёт: нарушения вне самоскладки | %d (max %.2f) | %d (max %.2f) |%n"
                        + "| NIS mean (согласованный состав, без пин-тиков) | %.3f (n=%d) | %.3f (n=%d) |%n"
                        + "| NIS mean пин-тики (отдельно) | — | %.3f (n=%d) |%n"
                        + "| bTerm-тиков (информационно, не гейт) | — | %d |%n"
                        + "| окна апексов с данными / лидер∈gokje на апексе | %d/10 | %d из них %d |%n"
                        + "| тиков v_impl>100 в окнах (вне санкций) | %d | %d |%n"
                        + "| exit-скачков (печать выше) | — | %d (max %.1f м) |%n",
                base.newTrips().size(), g.newTrips().size(),
                base.flightViolInFold(), base.flightMaxInFold(), g.flightViolInFold(), g.flightMaxInFold(),
                base.flightViolOutFold(), base.flightMaxOutFold(), g.flightViolOutFold(), g.flightMaxOutFold(),
                base.nisMeanShared(), base.nisN(), g.nisMeanShared(), g.nisN(),
                g.nisMeanPinned(), g.nisPinnedN(),
                g.bTermTicks(),
                base.windowsWithData(), g.windowsWithData(), g.windowsLeaderGokjeAtApex(),
                base.vImplOver100InWindows(), g.vImplOver100InWindows(),
                g.exitJumps().size(),
                g.exitJumps().isEmpty() ? 0.0 : g.exitJumps().stream().mapToDouble(d -> d).max().orElse(0)));

        md.append("\n## Пособытийный дифф NEW_TRIP (нетто-инвариант Р-6, окно 1200 с)\n\n");
        long lost = 0;
        for (NewTripEvent b : base.newTrips()) {
            NewTripEvent match = g.newTrips().stream()
                    .filter(e -> e.veh8().equals(b.veh8()) && Math.abs(e.epochMs() - b.epochMs()) <= 1_200_000)
                    .findFirst().orElse(null);
            if (match == null) {
                lost++;
                md.append(String.format("- ПОТЕРЯН в G′: %s @%s%n", b.veh8(), Instant.ofEpochMilli(b.epochMs())));
            }
        }
        long unexplained = 0;
        for (NewTripEvent e : g.newTrips()) {
            NewTripEvent match = base.newTrips().stream()
                    .filter(b -> b.veh8().equals(e.veh8()) && Math.abs(e.epochMs() - b.epochMs()) <= 1_200_000)
                    .findFirst().orElse(null);
            if (match == null) {
                if (e.leader().startsWith("61-gokje")) {
                    md.append(String.format("- НОВЫЙ в G′ (класс: легитимный выход через gokje-пару): %s @%s → %s%n",
                            e.veh8(), Instant.ofEpochMilli(e.epochMs()), e.leader()));
                } else {
                    unexplained++;
                    md.append(String.format("- НЕОБЪЯСНЁННЫЙ в G′: %s @%s → %s%n",
                            e.veh8(), Instant.ofEpochMilli(e.epochMs()), e.leader()));
                }
            }
        }
        md.append(String.format(Locale.ROOT, "%nИтог: потерянных %d, необъяснённых %d.%n", lost, unexplained));
        Files.writeString(Path.of("docs", "data", "b12_1_corpus_check.md"), md.toString());
        System.out.print(md);

        assertThat(lost).as("Р-6: потерянных NEW_TRIP = 0").isZero();
        assertThat(unexplained).as("Р-6: необъяснённых NEW_TRIP = 0").isZero();
        assertThat(g.flightViolOutFold())
                .as("полёт G′ ≤ base61 вне самоскладки")
                .isLessThanOrEqualTo(base.flightViolOutFold());
        assertThat(g.vImplOver100InWindows()).as("v_impl>100 в окнах апексов = 0").isZero();
        for (double jump : g.exitJumps()) {
            assertThat(jump).as("exit-скачок ≤ R_term_61 + D_reanchor = 1250 м")
                    .isLessThanOrEqualTo(EXIT_JUMP_CAP_M);
        }
        assertThat(g.windowsLeaderGokjeAtApex())
                .as("лидер ∈ gokje-семейству на моментах апексов (окна с данными)")
                .isEqualTo(g.windowsWithData());
    }
}
