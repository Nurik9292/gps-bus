package biz.ugur.busroutebackend.replay.a10;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.pipeline.CorpusLoader;
import biz.ugur.busroutebackend.replay.pipeline.Episode;
import biz.ugur.busroutebackend.replay.variants.RingCutout;
import biz.ugur.busroutebackend.replay.variants.VariantCatalog;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class A10Phase1Test {

    private static final RingCutout.Bbox RING_BBOX = new RingCutout.Bbox(58.145, 58.215, 38.038, 38.09);
    private static final CoreConfig CFG = CoreConfig.defaults();
    private static Path corpusDir;
    private static List<Episode> episodes;
    private static Map<String, RouteTopology> plainTopo;

    @BeforeAll
    static void loadCorpus() {
        String dir = System.getProperty("a10.corpus.dir");
        Assumptions.assumeTrue(dir != null && !dir.isBlank(),
                "A10 corpus runs only with -Da10.corpus.dir=<frozen corpus>");
        corpusDir = Path.of(dir);
        episodes = CorpusLoader.load(corpusDir, CFG.tMaxSec(), 10);
        plainTopo = A10Support.geometryMap();
        System.out.printf("A10 corpus %s: эпизодов %d%n", corpusDir, episodes.size());
    }

    @Test
    void a100BankConfigFactAndRerun25WithVariants() throws IOException {
        StringBuilder md = new StringBuilder();
        md.append("# A10.0 — конфиг-факт банка day-1 и перепрогон 25 с вариантами\n\n");
        md.append("## Таблица route → состав банка в day-1 (факт кода `CorpusReplayTest.buildGeometryMap`)\n\n");
        md.append("| route | режим | состав гипотез (variant_id → L, км) |\n|---|---|---|\n");
        for (var e : plainTopo.entrySet()) {
            StringBuilder comp = new StringBuilder();
            for (GeometryFixture g : e.getValue().allGeometries()) {
                if (comp.length() > 0) comp.append("; ");
                comp.append(String.format(Locale.ROOT, "%s#d%d → %.1f",
                        g.routeNumber(), g.direction(), g.totalMeters() / 1000.0));
            }
            md.append(String.format(Locale.ROOT, "| %s | bank {d, d′} (вариантов НЕТ) | %s |%n",
                    e.getKey(), comp));
        }
        md.append("\nВариантные гипотезы A9 (`VariantCatalog`/`RingCutout`) в day-1 конвейер НЕ подключались —\n");
        md.append("жили только в `Variant25*Test` на синтетике. Вердикт для гейта: вариантный банк 25 = ВЫКЛЮЧЕН.\n\n");

        RouteTopology banked25 = A10Support.withShortVariants("25", RING_BBOX);
        md.append("## Перепрогон эпизодов 25 (корпус Фазы 1): {d, d′} → {d, d′, short-d, short-d′}\n\n");
        md.append("| Борт (8) | Фиксы | Полёт max ДО | наруш. ДО | смены ДО | Полёт max ПОСЛЕ | наруш. ПОСЛЕ | смены ПОСЛЕ | RECOVERING до→после |\n");
        md.append("|---|---|---|---|---|---|---|---|---|\n");
        long violBefore = 0;
        long violAfter = 0;
        double maxBefore = 0;
        double maxAfter = 0;
        for (Episode ep : episodes) {
            if (!ep.routeNumber().equals("25")) continue;
            var before = A10Support.run(ep, plainTopo.get("25"), CFG, false);
            var after = A10Support.run(ep, banked25, CFG, false);
            violBefore += before.flightViolations();
            violAfter += after.flightViolations();
            maxBefore = Math.max(maxBefore, before.flightMaxRatio());
            maxAfter = Math.max(maxAfter, after.flightMaxRatio());
            md.append(String.format(Locale.ROOT, "| %s | %d | %.2f | %d | %d | %.2f | %d | %d | %d→%d |%n",
                    ep.vehicleId().substring(0, 8), ep.fixes().size(),
                    before.flightMaxRatio(), before.flightViolations(), before.leaderSwitches(),
                    after.flightMaxRatio(), after.flightViolations(), after.leaderSwitches(),
                    before.recoveringSpells(), after.recoveringSpells()));
        }
        md.append(String.format(Locale.ROOT,
                "%n**Итог 25**: нарушения %d → %d; max ratio %.2f → %.2f.%n",
                violBefore, violAfter, maxBefore, maxAfter));
        Files.writeString(Path.of("docs", "data", "a10_25_bank_rerun.md"), md.toString());
        System.out.print(md);
        assertThat(md.length()).isPositive();
    }

    @Test
    void a100FlightDumpsFor25And27() throws IOException {
        dumpEpisode("25", "caa8789f", plainTopo.get("25"));
        dumpEpisode("25", "ff5051e1", plainTopo.get("25"));
        dumpEpisode("27", "1275ff16", plainTopo.get("27"));
    }

    private void dumpEpisode(String route, String vehPrefix, RouteTopology topo) throws IOException {
        Episode target = null;
        for (Episode ep : episodes) {
            if (!ep.routeNumber().equals(route) || !ep.vehicleId().startsWith(vehPrefix)) continue;
            var out = A10Support.run(ep, topo, CFG, false);
            if (out.flightViolations() > 0 && (target == null || ep.fixes().size() > target.fixes().size())) {
                target = ep;
            }
        }
        assertThat(target).as("эпизод с нарушением для %s/%s найден", route, vehPrefix).isNotNull();
        var out = A10Support.run(target, topo, CFG, true);

        List<Integer> violationIdx = new ArrayList<>();
        for (int i = 1; i < out.ticks().size(); i++) {
            var t = out.ticks().get(i);
            var p = out.ticks().get(i - 1);
            if (t.stepRatio() > A10Support.FLIGHT_K && !t.sanctioned() && !p.sanctioned()) {
                violationIdx.add(i);
            }
        }

        StringBuilder md = new StringBuilder();
        md.append(String.format(Locale.ROOT, "# A10 полёт-дамп: маршрут %s, борт %s%n%n", route, target.vehicleId()));
        md.append(String.format(Locale.ROOT,
                "Эпизод: фиксов %d, окно %s → %s (Ашхабад UTC+5), полёт max %.2f, нарушений %d, "
                        + "санкционированных скачков %d, смен лидера %d, RECOVERING-спеллов %d.%n%n",
                target.fixes().size(),
                target.fixes().get(0).timestamp().atZone(ZoneOffset.ofHours(5)).toLocalDateTime(),
                target.fixes().get(target.fixes().size() - 1).timestamp().atZone(ZoneOffset.ofHours(5)).toLocalDateTime(),
                out.flightMaxRatio(), out.flightViolations(), out.sanctionedJumps(), out.leaderSwitches(),
                out.recoveringSpells()));
        md.append("НЕ чинить — материал разбора (A10.0). Формула полёта: |Δp вещания|/(Δt·v_max), "
                + "нарушение при >1.5 вне санкций (ре-привязка/NEW_TRIP/смена лидера).\n\n");

        for (int v : violationIdx) {
            var vt = out.ticks().get(v);
            md.append(String.format(Locale.ROOT, "## Нарушение @ t=%.0fс (ts %s, ratio %.2f)%n%n",
                    vt.tSec(), Instant.ofEpochMilli(vt.epochMs()), vt.stepRatio()));
            md.append("| t,с | s,м | лидер | режим | ratio | санкц.? | dt,с | события |\n|---|---|---|---|---|---|---|---|\n");
            for (var t : out.ticks()) {
                if (Math.abs(t.tSec() - vt.tSec()) > 90) continue;
                md.append(String.format(Locale.ROOT, "| %.0f | %.0f | %s | %s | %.2f | %s | %.0f | %s |%n",
                        t.tSec(), t.s(), t.leader(), t.mode(), t.stepRatio(),
                        t.sanctioned() ? "да" : "", t.dtSec(),
                        t.events().isEmpty() ? "" : String.join(",", t.events())));
            }
            md.append('\n');
        }
        Path outFile = Path.of("docs", "data",
                String.format("a10_flight_%s_%s.md", route, vehPrefix));
        Files.writeString(outFile, md.toString());
        System.out.printf("A10 dump: %s (нарушений в дампе: %d)%n", outFile, violationIdx.size());
    }

    @Test
    void a101Bank61DerivationVerdictAndRerun() throws IOException {
        VariantCatalog catalog = VariantCatalog.loadClasspath("/variants/half_turns.csv");
        var r61 = catalog.get("61");
        double etalonShortKm = Math.min(r61.halfA().lengthKm(), r61.halfB().lengthKm());

        StringBuilder md = new StringBuilder();
        md.append("# A10.1 — деривация банка вариантов 61 (методика A9: ствол вне кольцевой зоны)\n\n");
        md.append(String.format(Locale.ROOT,
                "Справочник: оборот %.0f км; Ýarym A=%.0f км/%s мин, B=%.0f км/%s мин; "
                        + "эталон короткого варианта = %.0f км.%n%n",
                r61.totalKm(), r61.halfA().lengthKm(), "40", r61.halfB().lengthKm(), "80", etalonShortKm));

        boolean ok = true;
        List<GeometryFixture> shorts = new ArrayList<>();
        for (String dir : List.of("dir0", "dir1")) {
            GeometryFixture full = GeometryFixture.loadClasspath("/fixtures/geometry/route-61-" + dir.replace("dir", "dir") + ".json");
            try {
                var cut = RingCutout.trunkOutsideRingZone(full, RING_BBOX, "61-short");
                double km = cut.shortVariant().totalMeters() / 1000.0;
                boolean inEtalon = km >= etalonShortKm * 0.9 && km <= etalonShortKm * 1.1;
                boolean monotone = true;
                var st = cut.shortVariant().stops();
                for (int i = 1; i < st.size(); i++) {
                    if (st.get(i).sMeters() <= st.get(i - 1).sMeters()) monotone = false;
                }
                md.append(String.format(Locale.ROOT,
                        "- %s: full=%.1f км → ствол s=[%.0f..%.0f], short=%.2f км (эталон %.0f±10%%: %s), "
                                + "стопов %d (отброшено %d), монотонность %s, IsSimple(500м) %s%n",
                        dir, full.totalMeters() / 1000.0, cut.trunkStartS(), cut.trunkEndS(), km,
                        etalonShortKm, inEtalon ? "OK" : "МИМО",
                        st.size(), cut.stopsDropped(), monotone ? "OK" : "ИНВЕРСИИ",
                        RingCutout.isSimple(cut.shortVariant(), 500.0) ? "OK" : "УЗЛЫ"));
                if (inEtalon && monotone) shorts.add(cut.shortVariant());
                else ok = false;
            } catch (IllegalStateException e) {
                md.append(String.format("- %s: ДЕРИВАЦИЯ НЕВОЗМОЖНА — %s%n", dir, e.getMessage()));
                ok = false;
            }
        }

        if (!ok || shorts.size() < 2) {
            md.append("\n**ВЕРДИКТ: ФЛАГ — Γ_v короткого варианта 61 однозначно НЕ деривируется** ");
            md.append("(числа выше; лист 61 внутренне неконсистентен — открытие №5). ");
            md.append("61 остаётся single-line, метка `variant-full-line (до A10.1)` НЕ снимается. ");
            md.append("Геометрия не изобреталась.\n");
            Files.writeString(Path.of("docs", "data", "a10_61_variant_verdict.md"), md.toString());
            System.out.print(md);
            return;
        }

        RouteTopology banked = RouteTopology.thereAndBack(
                        GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json"),
                        GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json"))
                .withVariants(List.copyOf(shorts));
        md.append("\n## Перепрогон эпизодов 61 с банком\n\n");
        md.append("| Борт (8) | Фиксы | Полёт max ДО→ПОСЛЕ | наруш. ДО→ПОСЛЕ | реанкоры-спеллы ДО→ПОСЛЕ | смены ДО→ПОСЛЕ |\n|---|---|---|---|---|---|\n");
        long vb = 0;
        long va = 0;
        double mb = 0;
        double ma = 0;
        int n61 = 0;
        for (Episode ep : episodes) {
            if (!ep.routeNumber().equals("61")) continue;
            n61++;
            var before = A10Support.run(ep, plainTopo.get("61"), CFG, false);
            var after = A10Support.run(ep, banked, CFG, false);
            vb += before.flightViolations();
            va += after.flightViolations();
            mb = Math.max(mb, before.flightMaxRatio());
            ma = Math.max(ma, after.flightMaxRatio());
            md.append(String.format(Locale.ROOT, "| %s | %d | %.2f→%.2f | %d→%d | %d→%d | %d→%d |%n",
                    ep.vehicleId().substring(0, 8), ep.fixes().size(),
                    before.flightMaxRatio(), after.flightMaxRatio(),
                    before.flightViolations(), after.flightViolations(),
                    before.recoveringSpells(), after.recoveringSpells(),
                    before.leaderSwitches(), after.leaderSwitches()));
        }
        md.append(String.format(Locale.ROOT,
                "%n**Итог 61** (%d эпизодов): нарушения %d → %d; max ratio %.2f → %.2f.%n", n61, vb, va, mb, ma));
        Files.writeString(Path.of("docs", "data", "a10_61_variant_verdict.md"), md.toString());
        System.out.print(md);
    }

    @Test
    void a102NisBreakdownCsv() throws IOException {
        Path outDir = Path.of("target", "a10");
        Files.createDirectories(outDir);
        Path csv = outDir.resolve("nis_rows.csv");
        RouteTopology banked25 = A10Support.withShortVariants("25", RING_BBOX);
        StringBuilder sb = new StringBuilder("route,vehicle,mode,hdop,dt_sec,nis\n");
        int rows = 0;
        for (Episode ep : episodes) {
            RouteTopology topo = ep.routeNumber().equals("25") ? banked25 : plainTopo.get(ep.routeNumber());
            if (topo == null) continue;
            var out = A10Support.run(ep, topo, CFG, false);
            for (var r : out.nisRows()) {
                sb.append(String.format(Locale.ROOT, "%s,%s,%s,%s,%.1f,%.6f%n",
                        r.route(), r.vehicleId().substring(0, 8), r.mode(),
                        r.hdop() == null ? "" : r.hdop(), r.dtSec(), r.nis()));
                rows++;
            }
        }
        Files.writeString(csv, sb.toString());
        System.out.printf("A10.2: NIS-строк %d → %s%n", rows, csv);
        assertThat(rows).isPositive();
    }
}
