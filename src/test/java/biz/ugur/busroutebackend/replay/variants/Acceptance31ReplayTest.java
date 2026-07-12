package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.HypothesisBank;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.io.BufferedWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

class Acceptance31ReplayTest {

    private static final CoreConfig CFG = CoreConfig.defaults();

    private static RouteTopology banked61() {
        RouteTopology topo = RouteTopology
                .thereAndBack(Variant61FixturesTest.FULL_0, Variant61FixturesTest.FULL_1)
                .withVariants(List.of(Variant61FixturesTest.gokje0().shortVariant(),
                        Variant61FixturesTest.gokjeTail1().shortVariant()));
        String zone = System.getProperty("acc31.cityzone", "");
        if (!zone.isBlank()) {
            String[] p = zone.split(",");
            topo = topo.withCityZone(new RouteTopology.CityZone(
                    Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]), Double.parseDouble(p[3])));
        }
        return topo;
    }

    @Test
    @EnabledIfSystemProperty(named = "acc31.corpus", matches = ".+")
    void replayCorpusRoute61PerVehicleContinuous() throws Exception {
        Path corpus = Path.of(System.getProperty("acc31.corpus"));
        Path dumpPath = Path.of(System.getProperty("acc31.dump"));
        String bankDumpProp = System.getProperty("acc31.bankdump", "");
        List<Episode> episodes = CorpusLoader.load(corpus, 1e9, 1);
        Map<String, List<GpsFix>> byVehicle = new TreeMap<>();
        for (Episode ep : episodes) {
            if (!ep.routeNumber().equals("61")) continue;
            byVehicle.computeIfAbsent(ep.vehicleId(), k -> new ArrayList<>()).addAll(ep.fixes());
        }
        long totalTicks = 0;
        try (BufferedWriter w = Files.newBufferedWriter(dumpPath);
             Writer bw = bankDumpProp.isBlank()
                     ? Writer.nullWriter() : Files.newBufferedWriter(Path.of(bankDumpProp))) {
            w.write("vid8|ts|mode|leader|s|dir|tripId|partial\n");
            if (!bankDumpProp.isBlank()) {
                bw.write("vid8|ts|leader|effLeader|cand|effCand|candProg|freezeGate\n");
            }
            for (Map.Entry<String, List<GpsFix>> e : byVehicle.entrySet()) {
                List<GpsFix> fixes = e.getValue();
                fixes.sort(Comparator.comparing(GpsFix::timestamp));
                MotionFilterCore core = new MotionFilterCore(CFG);
                core.reset();
                RouteTopology topo = banked61();
                String vid8 = e.getKey().substring(0, 8);
                for (GpsFix fx : fixes) {
                    var est = core.onFix(fx, topo);
                    w.write(String.format(Locale.ROOT, "%s|%s|%s|%s|%.1f|%d|%d|%s%n",
                            vid8, fx.timestamp(), est.mode(),
                            core.bank().leader().variantId(), est.s(), core.direction(),
                            core.tripId(), core.currentTripPartial() ? "y" : "n"));
                    if (!bankDumpProp.isBlank()) {
                        writeBankRow(bw, core, vid8, fx);
                    }
                    totalTicks++;
                }
            }
        }
        System.out.printf("acc31: бортов=%d, тиков=%d, дамп=%s%n",
                byVehicle.size(), totalTicks, dumpPath);
        assertThat(totalTicks).isPositive();
    }

    private static void writeBankRow(Writer bw, MotionFilterCore core,
                                     String vid8, GpsFix fx) throws Exception {
        HypothesisBank bank = core.bank();
        HypothesisBank.Hypothesis leader = bank.leader();
        HypothesisBank.Hypothesis best = null;
        for (HypothesisBank.Hypothesis h : bank.hypotheses()) {
            if (h == leader) continue;
            if (best == null || bank.effectiveScoreOf(h) > bank.effectiveScoreOf(best)) {
                best = h;
            }
        }
        bw.write(String.format(Locale.ROOT, "%s|%s|%s|%.3f|%s|%.3f|%d|%s%n",
                vid8, fx.timestamp(), leader.variantId(), bank.effectiveScoreOf(leader),
                best == null ? "-" : best.variantId(),
                best == null ? 0.0 : bank.effectiveScoreOf(best),
                best == null ? 0 : best.progressStreak(),
                core.freezeReanchorGateActive() ? "y" : "n"));
    }
}
