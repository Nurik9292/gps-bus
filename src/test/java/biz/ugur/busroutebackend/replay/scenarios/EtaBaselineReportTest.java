package biz.ugur.busroutebackend.replay.scenarios;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.PredictionModel;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.MotionFilterCore;
import biz.ugur.busroutebackend.replay.core.StopAware;
import biz.ugur.busroutebackend.replay.models.GeometricSnapModel;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EtaBaselineReportTest {

    private static final GeometryFixture G =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;

    private record Buckets(List<Double> h60, List<Double> h120, List<Double> h300) {
        Buckets() {
            this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        void add(double horizon, double err) {
            if (horizon <= 60) h60.add(err);
            else if (horizon <= 120) h120.add(err);
            else if (horizon <= 300) h300.add(err);
        }
    }

    @Test
    void nullModelBaselineTableSavedAsReportArtifact() throws IOException {
        Buckets core = new Buckets();
        Buckets nul = new Buckets();
        for (long seed = 440; seed < 445; seed++) {
            SyntheticScenario.MultiStopTrack track = SyntheticScenario.multiStopRun(G,
                    SyntheticScenario.Params.defaults(seed, "8", 0),
                    2000, 9000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), false);
            Map<String, Double> factArr = new HashMap<>();
            for (var v : track.visits()) factArr.put(v.stopId(), v.tArrivalSec());

            MotionFilterCore filter = new MotionFilterCore(CFG);
            filter.reset();
            GeometricSnapModel snap = new GeometricSnapModel();
            snap.reset();

            for (int i = 0; i < track.fixes().size(); i++) {
                double tNow = track.truth().get(i)[0];
                filter.onFix(track.fixes().get(i), G);
                PredictionModel.Estimate se = snap.onFix(track.fixes().get(i), G);

                for (StopAware.Eta eta : filter.etas()) {
                    Double fact = factArr.get(eta.stopId());
                    if (fact == null || !eta.reliable() || fact <= tNow) continue;
                    core.add(eta.etaSec(), Math.abs(tNow + eta.etaSec() - fact));
                }
                for (GeometryFixture.StopPoint sp : G.stops()) {
                    Double fact = factArr.get(sp.stopId());
                    if (fact == null || fact <= tNow || sp.sMeters() <= se.s()) continue;
                    double etaNull = (sp.sMeters() - se.s()) / CFG.vTargetMs();
                    if (etaNull > 300) continue;
                    nul.add(etaNull, Math.abs(tNow + etaNull - fact));
                }
            }
        }

        String table = """
                # ETA-базлайн: нулевая модель vs ядро v3.1 (стенд A4/A5)

                Нулевая модель: `ETA = Δs / v_target` от снап-оценки (без фильтра, без dwell, без кинематики).
                Ядро: MotionFilterCore v1 (кинематика §7, стоп-слой §8, dwell-константа 20 с, без истории).
                Профиль: route-8-dir0, мультистоп 2000–9000 м, dwell 20 с ±30%%, σ=5 м, сиды 440–444.
                Метрика: p95 |ETA − факт прибытия| по горизонтам (детерминированный прогон, файл идемпотентен).

                | Горизонт | Нулевая модель (Δs/v_target) | Ядро v1 | n (ядро) |
                |---|---|---|---|
                | ≤ 60 с | %s с | %s с | %d |
                | ≤ 120 с | %s с | %s с | %d |
                | ≤ 300 с | %s с | %s с | %d |

                Назначение: деплой-аргументация и опорная точка ML-этапа (§15 плана миграции).
                Источник: `EtaBaselineReportTest` (генерируется прогоном стенда, не редактировать руками).
                """.formatted(
                fmt(p95(nul.h60())), fmt(p95(core.h60())), core.h60().size(),
                fmt(p95(nul.h120())), fmt(p95(core.h120())), core.h120().size(),
                fmt(p95(nul.h300())), fmt(p95(core.h300())), core.h300().size());

        Path out = Path.of("reports", "eta-baseline-null-model.md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, table);
        System.out.printf("BASELINE report: null p95=%s/%s/%s | core p95=%s/%s/%s → %s%n",
                fmt(p95(nul.h60())), fmt(p95(nul.h120())), fmt(p95(nul.h300())),
                fmt(p95(core.h60())), fmt(p95(core.h120())), fmt(p95(core.h300())), out);

        assertThat(p95(core.h120())).as("ядро лучше нулевой модели на 120с").isLessThan(p95(nul.h120()));
        assertThat(p95(core.h300())).as("ядро лучше нулевой модели на 300с").isLessThan(p95(nul.h300()));
    }

    private static String fmt(double v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static double p95(List<Double> xs) {
        if (xs.isEmpty()) return Double.NaN;
        List<Double> s = xs.stream().sorted().toList();
        return s.get((int) Math.floor(0.95 * (s.size() - 1)));
    }
}
