package biz.ugur.busroutebackend.replay.pipeline;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.GpsFix;
import biz.ugur.busroutebackend.replay.GpsFixJsonl;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.synth.SyntheticScenario;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CorpusReplayTest {

    private static final GeometryFixture G8_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir0.json");
    private static final GeometryFixture G8_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-8-dir1.json");
    private static final GeometryFixture G10_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-10-dir0.json");
    private static final GeometryFixture G10_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-10-dir1.json");
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static final double CRUISE = 12.5;

    private static final Map<String, RouteTopology> GEOMETRY_BY_ROUTE = buildGeometryMap();

    private static Map<String, RouteTopology> buildGeometryMap() {
        Map<String, RouteTopology> map = new java.util.TreeMap<>();
        map.put("8", RouteTopology.thereAndBack(G8_0, G8_1));
        map.put("10", RouteTopology.thereAndBack(G10_0, G10_1));
        map.put("61", RouteTopology.thereAndBack(
                        GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json"),
                        GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json"))
                .withVariants(List.of(
                        biz.ugur.busroutebackend.replay.variants.Variant61FixturesTest.gokje0().shortVariant(),
                        biz.ugur.busroutebackend.replay.variants.Variant61FixturesTest.gokjeTail1().shortVariant())));
        for (String route : List.of("25", "62", "63", "27", "48", "74", "80", "12", "97")) {
            map.put(route, RouteTopology.thereAndBack(
                    GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir0.json"),
                    GeometryFixture.loadClasspath("/fixtures/geometry/route-" + route + "-dir1.json")));
        }
        return map;
    }

    @Test
    void corpusReplayEndToEndSingleCommandDeterministic() throws IOException {
        String override = System.getProperty("corpus.dir");
        Path corpusDir;
        String label;
        if (override != null && !override.isBlank()) {
            corpusDir = Path.of(override);
            label = corpusDir.getFileName().toString();
        } else {
            corpusDir = Path.of("target", "corpus-mini");
            label = "mini-corpus (синтетика A4/A5-профилей)";
            generateMiniCorpus(corpusDir);
        }

        String report1 = runPipeline(corpusDir, label);
        String report2 = runPipeline(corpusDir, label);
        assertThat(report2).as("SHA-детерминизм: два прогона дают идентичный отчёт").isEqualTo(report1);

        Path out = Path.of("reports", override == null
                ? "replay-mini-corpus.md"
                : "replay-" + label + ".md");
        Files.createDirectories(out.getParent());
        Files.writeString(out, report1);
        System.out.printf("Конвейер: отчёт → %s (SHA %s)%n", out, CorpusReplayReport.sha256(report1));

        if (override == null) {
            List<Episode> episodes = CorpusLoader.load(corpusDir, CFG.tMaxSec(), 10);
            assertThat(episodes)
                    .as("нарезка: 3 борта + сплит по gap>T_max у четвёртого = 5 эпизодов")
                    .hasSize(5);
            assertThat(episodes.stream().filter(e -> e.vehicleId().equals("veh-split")).count())
                    .as("борт с 30-мин разрывом разрезан на 2 эпизода")
                    .isEqualTo(2);
            assertThat(report1).contains("veh-mini-a", "veh-mini-b", "veh-turn", "veh-split");
            assertThat(report1).contains("ETA p95", "NIS");
        }
    }

    private String runPipeline(Path corpusDir, String label) {
        List<Episode> episodes = CorpusLoader.load(corpusDir, CFG.tMaxSec(), 10);
        List<EpisodeReplayRunner.EpisodeStats> stats = new ArrayList<>();
        List<Episode> skipped = new ArrayList<>();
        for (Episode ep : episodes) {
            RouteTopology topo = GEOMETRY_BY_ROUTE.get(ep.routeNumber());
            if (topo == null) {
                skipped.add(ep);
                continue;
            }
            stats.add(EpisodeReplayRunner.run(ep, topo, CFG));
        }
        return CorpusReplayReport.render(label, stats, skipped);
    }

    private void generateMiniCorpus(Path dir) throws IOException {
        Files.createDirectories(dir);
        try (var files = Files.walk(dir)) {
            files.filter(Files::isRegularFile).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        }

        var trackA = SyntheticScenario.multiStopRun(G8_0,
                params(700, "veh-mini-a", "8", "2026-07-03T06:00:00Z"),
                2000, 9000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), false);
        GpsFixJsonl.write(dir.resolve("veh-mini-a.jsonl"), trackA.fixes());

        var trackB = SyntheticScenario.multiStopRun(G8_0,
                params(701, "veh-mini-b", "8", "2026-07-03T06:05:00Z"),
                2000, 9000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), true);
        GpsFixJsonl.write(dir.resolve("veh-mini-b.jsonl"), trackB.fixes());

        var trackC = SyntheticScenario.terminalTurnRun(G10_0, G10_1,
                params(702, "veh-turn", "10", "2026-07-03T06:10:00Z"),
                G10_0.totalMeters() - 2000, CRUISE, 1.0, 240, 2100, 20, 0.3);
        GpsFixJsonl.write(dir.resolve("veh-turn.jsonl"), trackC.fixes());

        var trackD1 = SyntheticScenario.multiStopRun(G8_0,
                params(703, "veh-split", "8", "2026-07-03T06:00:00Z"),
                2000, 6000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), false);
        var trackD2 = SyntheticScenario.multiStopRun(G8_0,
                params(704, "veh-split", "8", "2026-07-03T07:30:00Z"),
                2000, 6000, CRUISE, 1.0, CFG.dwellExpectedSec(), 0.3, Set.of(), false);
        List<GpsFix> merged = new ArrayList<>(trackD1.fixes());
        merged.addAll(trackD2.fixes());
        GpsFixJsonl.write(dir.resolve("veh-split.jsonl"), merged);
    }

    private static SyntheticScenario.Params params(long seed, String vehicleId, String route, String startIso) {
        return new SyntheticScenario.Params(seed, 7.0, 5.0, 5.0,
                Instant.parse(startIso), vehicleId, "MINI " + seed, route, 0);
    }
}
