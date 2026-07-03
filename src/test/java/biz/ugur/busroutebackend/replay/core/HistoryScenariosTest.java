package biz.ugur.busroutebackend.replay.core;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.history.HistoryAccumulator;
import biz.ugur.busroutebackend.replay.history.SegmentDwellHistory;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

import static org.assertj.core.api.Assertions.assertThat;

class HistoryScenariosTest {

    private static final GeometryFixture G =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;

    private static final ToDoubleFunction<String> HETEROGENEOUS_DWELL = stopId -> {
        int seq = G.stops().stream().filter(s -> s.stopId().equals(stopId))
                .findFirst().map(GeometryFixture.StopPoint::seq).orElse(0);
        return switch (seq % 3) {
            case 0 -> 10.0;
            case 1 -> 20.0;
            default -> 45.0;
        };
    };

    private SyntheticScenario.MultiStopTrack run(long seed, ToDoubleFunction<String> dwellFn,
                                                 Set<String> skip) {
        return SyntheticScenario.multiStopRun(G,
                SyntheticScenario.Params.defaults(seed, "8", 0),
                2000, 9000, CRUISE, 1.0, dwellFn, 0.3, skip, false);
    }

    private record Headline(double p120, double p300, int n120, int n300) {}

    private Headline headline(List<Long> seeds, ToDoubleFunction<String> dwellFn,
                              SegmentDwellHistory history) {
        List<Double> h120 = new ArrayList<>();
        List<Double> h300 = new ArrayList<>();
        for (long seed : seeds) {
            SyntheticScenario.MultiStopTrack track = run(seed, dwellFn, Set.of());
            MotionFilterCore core = new MotionFilterCore(CFG).withHistory(history);
            core.reset();
            Map<String, Double> factArr = new HashMap<>();
            for (var v : track.visits()) factArr.put(v.stopId(), v.tArrivalSec());
            for (int i = 0; i < track.fixes().size(); i++) {
                core.onFix(track.fixes().get(i), G);
                double tNow = track.truth().get(i)[0];
                for (StopAware.Eta eta : core.etas()) {
                    Double fact = factArr.get(eta.stopId());
                    if (fact == null || !eta.reliable() || fact <= tNow) continue;
                    double err = Math.abs((tNow + eta.etaSec()) - fact);
                    if (eta.etaSec() > 60 && eta.etaSec() <= 120) h120.add(err);
                    else if (eta.etaSec() > 120 && eta.etaSec() <= 300) h300.add(err);
                }
            }
        }
        return new Headline(p95(h120), p95(h300), h120.size(), h300.size());
    }

    @Test
    void historyStrictlyBeatsConstantOnHeterogeneousDwellProfile() {
        HistoryAccumulator acc = new HistoryAccumulator(HistoryAccumulator.Config.p2Defaults());
        for (long seed = 500; seed < 505; seed++) {
            SyntheticScenario.MultiStopTrack track = run(seed, HETEROGENEOUS_DWELL, Set.of());
            acc.addRun(track.fixes(), G);
        }
        SegmentDwellHistory hist = acc.build();

        long stopsWithHistory = G.stops().stream()
                .filter(s -> hist.dwellSec(s.stopId(), CFG.historyNMin()).isPresent()).count();
        assertThat(stopsWithHistory).as("dwell-история накоплена (n≥%d) по большинству стопов", CFG.historyNMin())
                .isGreaterThanOrEqualTo(5);

        List<Long> evalSeeds = List.of(510L, 511L, 512L, 513L, 514L);
        Headline with = headline(evalSeeds, HETEROGENEOUS_DWELL, hist);
        Headline without = headline(evalSeeds, HETEROGENEOUS_DWELL, SegmentDwellHistory.empty());

        System.out.printf("HISTORY vs CONST (dwell 10/20/45с, те же сиды):%n");
        System.out.printf("  h<=120s: const=%.1fs (n=%d) | history=%.1fs (n=%d)%n",
                without.p120(), without.n120(), with.p120(), with.n120());
        System.out.printf("  h<=300s: const=%.1fs (n=%d) | history=%.1fs (n=%d)%n",
                without.p300(), without.n300(), with.p300(), with.n300());

        assertThat(with.p120()).as("headline 120с с историей СТРОГО лучше константы")
                .isLessThan(without.p120());
        assertThat(with.p300()).as("headline 300с с историей СТРОГО лучше константы")
                .isLessThan(without.p300());
    }

    @Test
    void oodStopWithoutHistoryFallsBackWithoutBreakingThresholds() {
        String oodStop = G.stops().stream()
                .filter(s -> s.sMeters() > 5000 && s.sMeters() < 7000)
                .findFirst().orElseThrow().stopId();
        ToDoubleFunction<String> dwellFn = stopId ->
                stopId.equals(oodStop) ? CFG.dwellExpectedSec() : HETEROGENEOUS_DWELL.applyAsDouble(stopId);

        HistoryAccumulator acc = new HistoryAccumulator(HistoryAccumulator.Config.p2Defaults());
        for (long seed = 520; seed < 525; seed++) {
            SyntheticScenario.MultiStopTrack track = run(seed, dwellFn, Set.of(oodStop));
            acc.addRun(track.fixes(), G);
        }
        SegmentDwellHistory hist = acc.build();
        assertThat(hist.dwellSec(oodStop, 1))
                .as("OOD-остановка в обучении скипалась → записей нет")
                .isEmpty();

        Headline with = headline(List.of(530L, 531L, 532L), dwellFn, hist);
        System.out.printf("OOD fallback: h<=120s=%.1fs (порог 25) | h<=300s=%.1fs (порог 60)%n",
                with.p120(), with.p300());
        assertThat(with.p120()).as("fallback 20с на OOD-остановке не роняет порог v1 (120с)")
                .isLessThanOrEqualTo(25.0);
        assertThat(with.p300()).as("fallback 20с на OOD-остановке не роняет порог v1 (300с)")
                .isLessThanOrEqualTo(60.0);
    }

    @Test
    void dwellOutlierAboveDwellMaxDoesNotEnterHistory() {
        String outlierStop = G.stops().stream()
                .filter(s -> s.sMeters() > 4000 && s.sMeters() < 6000)
                .findFirst().orElseThrow().stopId();
        ToDoubleFunction<String> dwellFn = stopId ->
                stopId.equals(outlierStop) ? 900.0 : 20.0;

        HistoryAccumulator acc = new HistoryAccumulator(HistoryAccumulator.Config.p2Defaults());
        SyntheticScenario.MultiStopTrack track = run(540, dwellFn, Set.of());
        acc.addRun(track.fixes(), G);
        SegmentDwellHistory hist = acc.build();

        assertThat(hist.dwellSec(outlierStop, 1))
                .as("выброс dwell>dwell_max=%.0fс не попал в историю", CFG.dwellMaxSec())
                .isEmpty();
        long normalStopsRecorded = G.stops().stream()
                .filter(s -> !s.stopId().equals(outlierStop))
                .filter(s -> hist.dwellSec(s.stopId(), 1).isPresent()).count();
        assertThat(normalStopsRecorded).as("нормальные стопы того же прогона записаны")
                .isGreaterThanOrEqualTo(3);
    }

    @Test
    void hourBinAssignedInLocalAshgabatZoneAcrossMidnight() {
        var lateEvening = new SyntheticScenario.Params(560, 7.0, 5.0, 5.0,
                java.time.Instant.parse("2026-07-03T18:40:00Z"), "veh-syn-560", "SYN 560", "8", 0);
        SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(G,
                lateEvening, 2000, 9000, CRUISE, 1.0, HETEROGENEOUS_DWELL, 0.3, Set.of(), false);
        HistoryAccumulator acc = new HistoryAccumulator(HistoryAccumulator.Config.p2Defaults());
        acc.addRun(track.fixes(), G);
        var keys = acc.build().segTravelByKey().keySet();
        assertThat(keys).as("18:40Z = 23:40 Asia/Ashgabat → бин локального часа 23, пятница")
                .isNotEmpty()
                .allMatch(k -> k.contains("|h23|") && k.endsWith("|wd"));
        assertThat(keys).as("UTC-бин 18 не присваивается").noneMatch(k -> k.contains("|h18|"));

        var pastMidnightLocal = new SyntheticScenario.Params(561, 7.0, 5.0, 5.0,
                java.time.Instant.parse("2026-07-03T20:30:00Z"), "veh-syn-561", "SYN 561", "8", 0);
        SyntheticScenario.MultiStopTrack track2 = SyntheticScenario.multiStopRun(G,
                pastMidnightLocal, 2000, 9000, CRUISE, 1.0, HETEROGENEOUS_DWELL, 0.3, Set.of(), false);
        HistoryAccumulator acc2 = new HistoryAccumulator(HistoryAccumulator.Config.p2Defaults());
        acc2.addRun(track2.fixes(), G);
        var keys2 = acc2.build().segTravelByKey().keySet();
        assertThat(keys2)
                .as("20:30Z пятницы = 01:30 субботы локально → weekend по локальной дате, бин 1")
                .isNotEmpty()
                .allMatch(k -> k.contains("|h1|") && k.endsWith("|we"));
    }

    private static double p95(List<Double> xs) {
        if (xs.isEmpty()) return Double.NaN;
        List<Double> s = xs.stream().sorted().toList();
        return s.get((int) Math.floor(0.95 * (s.size() - 1)));
    }
}
