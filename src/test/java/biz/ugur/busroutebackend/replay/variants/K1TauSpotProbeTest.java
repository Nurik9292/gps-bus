package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class K1TauSpotProbeTest {

    private static final CoreConfig CFG = CoreConfig.defaults();

    @Test
    @EnabledIfSystemProperty(named = "k1tau.corpus", matches = ".+")
    void printRuntimeTauStarvForVehicleWindow() throws Exception {
        Path corpus = Path.of(System.getProperty("k1tau.corpus"));
        String vehiclePrefix = System.getProperty("k1tau.vehicle");
        Instant from = Instant.parse(System.getProperty("k1tau.from"));
        Instant to = Instant.parse(System.getProperty("k1tau.to"));
        List<Episode> episodes = CorpusLoader.load(corpus, 1e9, 1);
        List<GpsFix> fixes = new ArrayList<>();
        for (Episode ep : episodes) {
            if (!ep.routeNumber().equals("61")) continue;
            if (!ep.vehicleId().startsWith(vehiclePrefix)) continue;
            fixes.addAll(ep.fixes());
        }
        fixes.sort(Comparator.comparing(GpsFix::timestamp));
        assertThat(fixes).isNotEmpty();

        RouteTopology topo = RouteTopology
                .thereAndBack(Variant61FixturesTest.FULL_0, Variant61FixturesTest.FULL_1)
                .withVariants(List.of(Variant61FixturesTest.gokje0().shortVariant(),
                        Variant61FixturesTest.gokjeTail1().shortVariant()));
        MotionFilterCore core = new MotionFilterCore(CFG);
        core.reset();
        long printed = 0;
        for (GpsFix fx : fixes) {
            double tauRuntime = Double.isNaN(core.lastLeaderSnapAtMs()) ? 0
                    : Math.max(0, (fx.timestamp().toEpochMilli() - core.lastLeaderSnapAtMs()) / 1000.0);
            var est = core.onFix(fx, topo);
            if (!fx.timestamp().isBefore(from) && !fx.timestamp().isAfter(to)) {
                System.out.printf(Locale.ROOT, "K1TAU %s %s tau_rt=%.1f mode=%s trip=%d%n",
                        vehiclePrefix, fx.timestamp(), tauRuntime, est.mode(), core.tripId());
                printed++;
            }
        }
        System.out.printf("K1TAU итог: фиксов в окне %d%n", printed);
        assertThat(printed).isPositive();
    }
}
