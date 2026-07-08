package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.HypothesisBank;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class A10DeltaSAttributionTest {

    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double DELTA_THRESHOLD = 0.25;
    private static final double SWITCH_WINDOW_SEC = 60;

    @Test
    @EnabledIfSystemProperty(named = "a10.attribution", matches = "true")
    void attributeDeltaSTicksPerRouteDir() {
        String corpusDir = System.getProperty("corpus.dir");
        assertThat(corpusDir).as("-Dcorpus.dir обязателен").isNotBlank();

        Map<String, RouteTopology> topoByRoute = new TreeMap<>();
        GeometryFixture f25d0 = Variant25FixturesTest.FULL_0;
        GeometryFixture f25d1 = Variant25FixturesTest.FULL_1;
        topoByRoute.put("25", RouteTopology.thereAndBack(f25d0, f25d1)
                .withVariants(List.of(Variant25FixturesTest.short0().shortVariant(),
                        Variant25FixturesTest.short1().shortVariant())));
        topoByRoute.put("61", RouteTopology.thereAndBack(
                        GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json"),
                        GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json"))
                .withVariants(List.of(Variant61FixturesTest.gokje0().shortVariant(),
                        Variant61FixturesTest.gokjeTail1().shortVariant())));

        Map<String, long[]> counters = new TreeMap<>();
        Map<String, List<String>> shortOutsideEpisodes = new TreeMap<>();

        for (Episode ep : CorpusLoader.load(Path.of(corpusDir), CFG.tMaxSec(), 10)) {
            RouteTopology topo = topoByRoute.get(ep.routeNumber());
            if (topo == null) continue;

            MotionFilterCore core = new MotionFilterCore(CFG);
            core.reset();
            List<double[]> ticks = new ArrayList<>();
            List<Double> switchTimes = new ArrayList<>();
            String prevLeader = null;
            long t0 = ep.fixes().get(0).timestamp().toEpochMilli();
            for (GpsFix fx : ep.fixes()) {
                var est = core.onFix(fx, topo);
                double t = (fx.timestamp().toEpochMilli() - t0) / 1000.0;
                HypothesisBank.Hypothesis leader = core.bank().leader();
                if (prevLeader != null && !prevLeader.equals(leader.variantId())) switchTimes.add(t);
                prevLeader = leader.variantId();
                HypothesisBank.Hypothesis challenger = null;
                for (HypothesisBank.Hypothesis h : core.bank().hypotheses()) {
                    if (h == leader) continue;
                    if (challenger == null || h.score() > challenger.score()) challenger = h;
                }
                if (challenger == null) continue;
                double deltaS = challenger.score() - leader.score();
                int challengerClass = challenger.variantId().contains("-short") ? 1
                        : challenger.direction() != leader.direction() ? 0 : 2;
                ticks.add(new double[]{t, deltaS, challengerClass, core.direction()});
            }
            String key = ep.routeNumber() + "/" + (ticks.isEmpty() ? "?" : (int) ticks.get(0)[3]);
            long[] c = counters.computeIfAbsent(key, k -> new long[6]);
            for (double[] tick : ticks) {
                if (tick[1] <= DELTA_THRESHOLD) continue;
                c[0]++;
                int cls = (int) tick[2];
                if (cls == 0) c[1]++;
                else if (cls == 1) c[2]++;
                else c[3]++;
                boolean nearSwitch = switchTimes.stream()
                        .anyMatch(st -> Math.abs(st - tick[0]) <= SWITCH_WINDOW_SEC);
                if (nearSwitch) c[4]++;
                if (cls == 1 && !nearSwitch) {
                    c[5]++;
                    shortOutsideEpisodes
                            .computeIfAbsent(key, k -> new ArrayList<>())
                            .add(ep.vehicleId().substring(0, 8) + "@"
                                    + ep.fixes().get(0).timestamp());
                }
            }
        }

        System.out.println("D3 п.2 — атрибуция тиков ΔS>" + DELTA_THRESHOLD + " (числа, не интерпретации):");
        System.out.println("| route/dir | тиков ΔS>0.25 | (а) d′ | (а) short | (а) иное | (б) в ±60с от смен | (в) short вне ±60с |");
        System.out.println("|---|---|---|---|---|---|---|");
        for (var e : counters.entrySet()) {
            long[] c = e.getValue();
            System.out.printf(Locale.ROOT, "| %s | %d | %d (%.0f%%) | %d (%.0f%%) | %d (%.0f%%) | %d (%.0f%%) | %d |%n",
                    e.getKey(), c[0],
                    c[1], pct(c[1], c[0]), c[2], pct(c[2], c[0]), c[3], pct(c[3], c[0]),
                    c[4], pct(c[4], c[0]), c[5]);
        }
        shortOutsideEpisodes.forEach((key, eps) -> {
            var unique = eps.stream().distinct().toList();
            System.out.println("(в) эпизоды-кандидаты " + key + ": "
                    + unique.subList(0, Math.min(10, unique.size())));
        });
    }

    private static double pct(long part, long total) {
        return total > 0 ? 100.0 * part / total : 0;
    }

    @Test
    @EnabledIfSystemProperty(named = "a10.f1rerun", matches = "true")
    void f1Rerun25WithVariantBank() throws Exception {
        String corpusDir = System.getProperty("corpus.dir");
        assertThat(corpusDir).isNotBlank();
        RouteTopology topo = RouteTopology.thereAndBack(
                        Variant25FixturesTest.FULL_0, Variant25FixturesTest.FULL_1)
                .withVariants(List.of(Variant25FixturesTest.short0().shortVariant(),
                        Variant25FixturesTest.short1().shortVariant()));
        System.out.println("=== П.5 перепрогон Ф1 (эпизоды 25, банк с вариантами) ===");
        System.out.println("состав банка: " + topo.allGeometries().stream()
                .map(g -> g.routeNumber() + "#d" + g.direction()
                        + String.format(Locale.ROOT, "→%.2fкм", g.totalMeters() / 1000))
                .toList());

        var digest = java.security.MessageDigest.getInstance("SHA-256");
        long shortLeaderTicks = 0;
        String shortFirst = null;
        String shortLast = null;
        List<Double> nisAll = new ArrayList<>();
        long nisOver = 0;
        long switches = 0;
        long switchesToShort = 0;
        long flightViolations = 0;
        double flightMax = 0;
        int episodes = 0;

        for (Episode ep : CorpusLoader.load(Path.of(corpusDir), CFG.tMaxSec(), 10)) {
            if (!ep.routeNumber().equals("25")) continue;
            episodes++;
            MotionFilterCore core = new MotionFilterCore(CFG);
            core.reset();
            String prevLeader = null;
            double[] prevGeo = null;
            double prevT = 0;
            long t0 = ep.fixes().get(0).timestamp().toEpochMilli();
            for (GpsFix fx : ep.fixes()) {
                var est = core.onFix(fx, topo);
                double t = (fx.timestamp().toEpochMilli() - t0) / 1000.0;
                String leader = core.bank().leader().variantId();
                double[] geo = core.bank().leader().geom().pointAtS(est.s());
                digest.update(String.format(Locale.ROOT, "%s|%s|%.1f|%s%n",
                        fx.timestamp(), leader, est.s(), est.mode()).getBytes());
                boolean switched = prevLeader != null && !prevLeader.equals(leader);
                if (switched) {
                    switches++;
                    if (leader.contains("-short")) switchesToShort++;
                }
                if (leader.contains("-short")) {
                    shortLeaderTicks++;
                    String local = fx.timestamp().plusSeconds(5 * 3600).toString().substring(0, 16);
                    if (shortFirst == null) shortFirst = local;
                    shortLast = local;
                }
                boolean sanctioned = est.mode().equals("RECOVERING") || est.mode().equals("NEW_TRIP")
                        || switched;
                if (prevGeo != null && !sanctioned) {
                    double dt = Math.max(t - prevT, 1);
                    double ratio = biz.ugur.busroutebackend.replay.GeometryFixture.haversineMeters(
                            prevGeo[0], prevGeo[1], geo[0], geo[1]) / (dt * CFG.vMaxMs());
                    flightMax = Math.max(flightMax, ratio);
                    if (ratio > 1.5) flightViolations++;
                }
                if (core.lastUpdateAccepted() && core.lastInnovationVariance() > 0) {
                    double nis = core.lastInnovation() * core.lastInnovation()
                            / core.lastInnovationVariance();
                    nisAll.add(nis);
                    if (nis > 3.84) nisOver++;
                }
                prevLeader = leader;
                prevGeo = geo;
                prevT = t;
            }
        }
        String streamSha = java.util.HexFormat.of().formatHex(digest.digest()).substring(0, 12);
        double nisMedian = nisAll.isEmpty() ? Double.NaN
                : nisAll.stream().sorted().toList().get(nisAll.size() / 2);
        System.out.printf(Locale.ROOT,
                "(а) выходной поток: эпизодов 25 = %d, SHA-256(t|leader|s|mode) = %s%n"
                        + "(б) тики лидерства short: n=%d, окно local: %s → %s%n"
                        + "(в) смен лидера: %d, из них на short: %d%n"
                        + "(г) NIS: n=%d, median=%.2f, >3.84: %.1f%%%n"
                        + "полёт: max=%.2f, нарушений=%d%n",
                episodes, streamSha, shortLeaderTicks,
                shortFirst, shortLast, switches, switchesToShort,
                nisAll.size(), nisMedian, pct(nisOver, nisAll.size()),
                flightMax, flightViolations);
    }
}
