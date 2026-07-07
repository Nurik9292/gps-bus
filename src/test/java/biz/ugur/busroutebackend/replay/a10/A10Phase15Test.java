package biz.ugur.busroutebackend.replay.a10;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.InputValidator;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.HypothesisBank;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import biz.ugur.busroutebackend.replay.variants.RingCutout;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A10Phase15Test {

    private static final RingCutout.Bbox RING_BBOX = new RingCutout.Bbox(58.145, 58.215, 38.038, 38.09);
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double SHORT_TOL_M = 1500;
    private static final double FULL_MARGIN_M = 3000;
    private static final double DROP_M = 2000;
    private static final int MIN_FIXES = 100;
    private static final double TURN_WINDOW_SEC = 180;

    private static List<Episode> episodes;
    private static final Map<String, GeometryFixture[]> FULL = new LinkedHashMap<>();
    private static final Map<String, RouteTopology> BANKED = new LinkedHashMap<>();
    private static final Map<String, double[]> L_SHORT = new LinkedHashMap<>();

    record ShortCandidate(Episode ep, int dir, double maxS, long maxSEpochMs, double dropM) {}

    @BeforeAll
    static void setUp() {
        String dir = System.getProperty("a10.corpus.dir");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(),
                "Phase 1.5 runs only with -Da10.corpus.dir=<frozen corpus>");
        episodes = CorpusLoader.load(Path.of(dir), CFG.tMaxSec(), 10);
        for (String route : List.of("25", "61")) {
            GeometryFixture f0 = GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir0.json");
            GeometryFixture f1 = GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir1.json");
            GeometryFixture s0 = RingCutout.trunkOutsideRingZone(f0, RING_BBOX, route + "-short").shortVariant();
            GeometryFixture s1 = RingCutout.trunkOutsideRingZone(f1, RING_BBOX, route + "-short").shortVariant();
            FULL.put(route, new GeometryFixture[]{f0, f1});
            BANKED.put(route, RouteTopology.thereAndBack(f0, f1).withVariants(List.of(s0, s1)));
            L_SHORT.put(route, new double[]{s0.totalMeters(), s1.totalMeters()});
        }
    }

    private static int pickDirection(Episode ep, GeometryFixture[] full) {
        double[] sum = new double[2];
        int n = 0;
        for (int i = 0; i < ep.fixes().size(); i += Math.max(1, ep.fixes().size() / 200)) {
            GpsFix fx = ep.fixes().get(i);
            for (int d = 0; d < 2; d++) {
                var p = full[d].projectOntoRange(fx.latitude(), fx.longitude(), 0, full[d].totalMeters(), 0);
                sum[d] += p.distMeters();
            }
            n++;
        }
        return sum[0] <= sum[1] ? 0 : 1;
    }

    private static double[] sSeries(Episode ep, GeometryFixture g) {
        double[] s = new double[ep.fixes().size()];
        for (int i = 0; i < ep.fixes().size(); i++) {
            GpsFix fx = ep.fixes().get(i);
            s[i] = g.projectOntoRange(fx.latitude(), fx.longitude(), 0, g.totalMeters(), 0).s();
        }
        return s;
    }

    private static List<ShortCandidate> classify(String route, StringBuilder md) {
        Map<String, int[]> counters = new LinkedHashMap<>();
        counters.put(route + "/0", new int[3]);
        counters.put(route + "/1", new int[3]);
        List<ShortCandidate> shorts = new ArrayList<>();
        List<String> shortIds = new ArrayList<>();
        for (Episode ep : episodes) {
            if (!ep.routeNumber().equals(route)) continue;
            GeometryFixture[] full = FULL.get(route);
            int dir = pickDirection(ep, full);
            double lShort = L_SHORT.get(route)[dir];
            double[] s = sSeries(ep, full[dir]);
            int argmax = 0;
            for (int i = 1; i < s.length; i++) if (s[i] > s[argmax]) argmax = i;
            double maxS = s[argmax];
            double minAfter = maxS;
            for (int i = argmax; i < s.length; i++) minAfter = Math.min(minAfter, s[i]);
            double drop = maxS - minAfter;
            int[] c = counters.get(route + "/" + dir);
            String cls;
            if (maxS >= lShort + FULL_MARGIN_M) {
                c[0]++;
                cls = "full";
            } else if (Math.abs(maxS - lShort) <= SHORT_TOL_M && drop >= DROP_M && ep.fixes().size() >= MIN_FIXES) {
                c[1]++;
                cls = "SHORT";
                shorts.add(new ShortCandidate(ep, dir, maxS,
                        ep.fixes().get(argmax).timestamp().toEpochMilli(), drop));
                shortIds.add(String.format(Locale.ROOT, "%s/d%d %s @%s (maxS=%.1fкм, drop=%.1fкм, фиксов %d)",
                        route, dir, ep.vehicleId().substring(0, 8),
                        ep.fixes().get(0).timestamp().atZone(ZoneOffset.ofHours(5)).toLocalDateTime(),
                        maxS / 1000, drop / 1000, ep.fixes().size()));
            } else {
                c[2]++;
                cls = "ambiguous";
            }
            if (!cls.equals("full") && !cls.equals("ambiguous")) continue;
        }
        md.append(String.format("%n### Маршрут %s (L_short: d0=%.2f км, d1=%.2f км)%n%n",
                route, L_SHORT.get(route)[0] / 1000, L_SHORT.get(route)[1] / 1000));
        md.append("| route/dir | full | short-кандидаты | ambiguous |\n|---|---|---|---|\n");
        for (var e : counters.entrySet()) {
            md.append(String.format("| %s | %d | %d | %d |%n", e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2]));
        }
        if (shortIds.isEmpty()) {
            md.append("\nShort-кандидатов: **0** (повтор детектора на day-3/day-5 корпусе — по приёмке).\n");
        } else {
            md.append("\nShort-кандидаты:\n");
            for (String id : shortIds) md.append("- ").append(id).append('\n');
        }
        return shorts;
    }

    record DsTick(double tSec, long epochMs, double dS, boolean inTurnWindow) {}

    private static List<DsTick> bankRun(Episode ep, RouteTopology topo, List<long[]> turnWindows,
                                        StringBuilder dump, long dumpCenterMs, GeometryFixture sRef) {
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        InputValidator validator = InputValidator.spec9Defaults();
        List<DsTick> out = new ArrayList<>();
        long t0 = ep.fixes().get(0).timestamp().toEpochMilli();
        String prevMode = "";
        for (GpsFix fx : ep.fixes()) {
            if (!validator.validate(fx).accepted()) continue;
            PredictionModel.Estimate est = core.onFix(fx, topo);
            HypothesisBank bank = core.bank();
            HypothesisBank.Hypothesis leader = bank.leader();
            double best = Double.NEGATIVE_INFINITY;
            for (HypothesisBank.Hypothesis h : bank.hypotheses()) {
                if (h != leader) best = Math.max(best, h.score());
            }
            double dS = best - leader.score();
            long ms = fx.timestamp().toEpochMilli();
            boolean inWin = false;
            for (long[] w : turnWindows) {
                if (ms >= w[0] && ms <= w[1]) inWin = true;
            }
            out.add(new DsTick((ms - t0) / 1000.0, ms, dS, inWin));

            if (dump != null && Math.abs(ms - dumpCenterMs) <= TURN_WINDOW_SEC * 1000) {
                double sMeas = sRef.projectOntoRange(fx.latitude(), fx.longitude(), 0, sRef.totalMeters(), 0).s();
                StringBuilder hs = new StringBuilder();
                for (HypothesisBank.Hypothesis h : bank.hypotheses()) {
                    var p = h.geom().projectOntoRange(fx.latitude(), fx.longitude(), 0, h.geom().totalMeters(), 0);
                    hs.append(String.format(Locale.ROOT, "%s d=%.0f %s S=%.2f%s | ",
                            h.variantId(), p.distMeters(), h.snappedLast() ? "acc" : "REJ",
                            h.score(), h == leader ? " ←лидер" : ""));
                }
                String reanchor = est.mode().equals("RECOVERING") && !prevMode.equals("RECOVERING") ? " ⚑реанкор" : "";
                dump.append(String.format(Locale.ROOT, "| %.0f | %.0f | %s | %.2f | %s%s |%n",
                        (ms - t0) / 1000.0, sMeas, est.mode(), dS, hs, reanchor));
            }
            prevMode = est.mode();
        }
        return out;
    }

    @Test
    void phase15SignalsShortTripsAndDeltaS() throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# A10 Фаза 1.5 — сигнальная разведка (корпус Фазы 1, манифест a10_corpus_manifest.md)\n");
        md.append("\nКлассификация по проекции на ПОЛНУЮ линию направления; критерии из ТЗ:\n");
        md.append(String.format(Locale.ROOT,
                "short: |maxS−L_short|≤%.1f км ∧ спад≥%.0f км ∧ ≥%d фиксов; full: maxS≥L_short+%.0f км.%n",
                SHORT_TOL_M / 1000, DROP_M / 1000, MIN_FIXES, FULL_MARGIN_M / 1000));

        md.append("\n## П.1 — детектор коротких рейсов\n");
        Map<String, List<ShortCandidate>> shortsByRoute = new LinkedHashMap<>();
        for (String route : List.of("25", "61")) {
            shortsByRoute.put(route, classify(route, md));
        }

        md.append("\n## П.2 — дампы разворотов (≤3)\n");
        int dumped = 0;
        Map<String, List<long[]>> turnWindows = new LinkedHashMap<>();
        for (var e : shortsByRoute.entrySet()) {
            for (ShortCandidate sc : e.getValue()) {
                turnWindows.computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                        .add(new long[]{sc.maxSEpochMs() - (long) (TURN_WINDOW_SEC * 1000),
                                sc.maxSEpochMs() + (long) (TURN_WINDOW_SEC * 1000)});
            }
        }
        for (var e : shortsByRoute.entrySet()) {
            for (ShortCandidate sc : e.getValue()) {
                if (dumped >= 3) break;
                StringBuilder dump = new StringBuilder();
                dump.append(String.format("# Дамп разворота short-кандидата: %s/d%d, борт %s%n%n",
                        e.getKey(), sc.dir(), sc.ep().vehicleId()));
                dump.append(String.format(Locale.ROOT,
                        "maxS=%.1f км @ %s; окно ±%.0f с. Банк: {full d0, full d1, short d0, short d1} (A9/ратиф. 61).%n%n",
                        sc.maxS() / 1000,
                        java.time.Instant.ofEpochMilli(sc.maxSEpochMs()).atZone(ZoneOffset.ofHours(5)).toLocalDateTime(),
                        TURN_WINDOW_SEC));
                dump.append("| t,с | s_meas,м | режим | ΔS | гипотезы {id d_snap acc/REJ S} | |\n|---|---|---|---|---|---|\n");
                bankRun(sc.ep(), BANKED.get(e.getKey()), List.of(), dump, sc.maxSEpochMs(),
                        FULL.get(e.getKey())[sc.dir()]);
                String name = String.format("a10_shorttrip_%s_%s.md", e.getKey(), sc.ep().vehicleId().substring(0, 8));
                Files.writeString(Path.of("docs", "data", name), dump.toString());
                md.append(String.format("- дамп: %s%n", name));
                dumped++;
            }
        }
        if (dumped == 0) md.append("- short-кандидатов нет → дампов нет (явный 0 по приёмке).\n");

        md.append("\n## П.3 — ΔS-статистика (лучший не-лидер − лидер), банк с вариантами\n\n");
        md.append("| route | тиков | p50 | p99 | max | ΔS>0.1 | ΔS>0.25 | тиков в окнах разворотов | p50/p99/max в окнах | >0.1 / >0.25 в окнах |\n");
        md.append("|---|---|---|---|---|---|---|---|---|---|\n");
        for (String route : List.of("25", "61")) {
            List<Double> all = new ArrayList<>();
            List<Double> win = new ArrayList<>();
            List<long[]> windows = turnWindows.getOrDefault(route, List.of());
            for (Episode ep : episodes) {
                if (!ep.routeNumber().equals(route)) continue;
                for (DsTick t : bankRun(ep, BANKED.get(route), windows, null, Long.MIN_VALUE, FULL.get(route)[0])) {
                    all.add(t.dS());
                    if (t.inTurnWindow()) win.add(t.dS());
                }
            }
            md.append(String.format(Locale.ROOT, "| %s | %d | %.3f | %.3f | %.3f | %d | %d | %d | %s | %s |%n",
                    route, all.size(), pct(all, 0.50), pct(all, 0.99), all.stream().mapToDouble(d -> d).max().orElse(Double.NaN),
                    all.stream().filter(d -> d > 0.1).count(), all.stream().filter(d -> d > 0.25).count(),
                    win.size(),
                    win.isEmpty() ? "—" : String.format(Locale.ROOT, "%.3f/%.3f/%.3f",
                            pct(win, 0.50), pct(win, 0.99), win.stream().mapToDouble(d -> d).max().orElse(Double.NaN)),
                    win.isEmpty() ? "—" : String.format(Locale.ROOT, "%d / %d",
                            win.stream().filter(d -> d > 0.1).count(), win.stream().filter(d -> d > 0.25).count())));
        }
        md.append("\nИнтерпретаций нет — числа (по ТЗ).\n");
        Files.writeString(Path.of("docs", "data", "a10_phase15_signals.md"), md.toString());
        System.out.print(md);
        assertThat(md.length()).isPositive();
    }

    private static double pct(List<Double> vals, double q) {
        if (vals.isEmpty()) return Double.NaN;
        List<Double> s = new ArrayList<>(vals);
        s.sort(Double::compareTo);
        return s.get(Math.min(s.size() - 1, (int) Math.round(q * (s.size() - 1))));
    }
}
