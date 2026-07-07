package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Variant25FixturesTest {

    static final RingCutout.Bbox RING_BBOX = new RingCutout.Bbox(58.145, 58.215, 38.038, 38.09);

    static final GeometryFixture FULL_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-25-dir0.json");
    static final GeometryFixture FULL_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-25-dir1.json");

    static final double SHORT_25_0_S_TURN_RATIFIED_M = 15790.0;

    static RingCutout.CutResult short0() {
        return RingCutout.prefixToS(FULL_0, SHORT_25_0_S_TURN_RATIFIED_M, "25-short");
    }

    static RingCutout.CutResult short0TailInactiveCandidate() {
        return RingCutout.suffixFromS(FULL_0, SHORT_25_0_S_TURN_RATIFIED_M, "25-short-tail-candidate");
    }

    static RingCutout.CutResult short0SupersededTrunkV1() {
        return RingCutout.trunkOutsideRingZone(FULL_0, RING_BBOX, "25-short-superseded-v1");
    }

    static RingCutout.CutResult short1() {
        return RingCutout.trunkOutsideRingZone(FULL_1, RING_BBOX, "25-short");
    }

    @Test
    void catalogLoadsAllRoutesAndClosureValidatorGreen() {
        VariantCatalog catalog = VariantCatalog.loadClasspath("/variants/half_turns.csv");
        assertThat(catalog.size()).as("справочник: 93 маршрута приказа").isEqualTo(93);
        List<VariantCatalog.RouteVariants> flags = catalog.closureFlags(0.10);
        System.out.printf("A9.0: Σ-валидатор (A+B vs оборот, >10%%): флагов=%d %s%n",
                flags.size(), flags.stream().map(VariantCatalog.RouteVariants::route).toList());
        assertThat(flags).as("Σ вариантов ↔ оборот сходится по всем маршрутам").isEmpty();

        var r25 = catalog.get("25");
        assertThat(r25.totalKm()).isEqualTo(70.0);
        assertThat(r25.halfA().lengthKm()).as("full-эталон Ýarym A").isEqualTo(53.0);
        assertThat(r25.halfB().lengthKm()).as("short-эталон Ýarym B").isEqualTo(17.0);
    }

    @Test
    void shortVariantCutFromRealGeometryMatchesYarymB() {
        VariantCatalog catalog = VariantCatalog.loadClasspath("/variants/half_turns.csv");
        double etalonKm = catalog.get("25").halfB().lengthKm();

        for (var cutCase : List.of(
                new Object[]{"dir0", short0(), FULL_0},
                new Object[]{"dir1", short1(), FULL_1})) {
            String label = (String) cutCase[0];
            RingCutout.CutResult cut = (RingCutout.CutResult) cutCase[1];
            GeometryFixture full = (GeometryFixture) cutCase[2];
            GeometryFixture shortG = cut.shortVariant();

            double shortKm = shortG.totalMeters() / 1000.0;
            double deltaPct = (shortKm - etalonKm) / etalonKm * 100;
            System.out.printf("A9.1 %s: full=%.1fкм, short-вариант s=[%.0f..%.0f], "
                            + "L=%.2fкм, Δ vs Ýarym B %.0f км = %+.1f%% (допуск ±10%%), "
                            + "стопов short=%d (отброшено %d)%n",
                    label, full.totalMeters() / 1000.0, cut.trunkStartS(), cut.trunkEndS(),
                    shortKm, etalonKm, deltaPct,
                    shortG.stops().size(), cut.stopsDropped());

            assertThat(shortKm)
                    .as("%s: L_short в эталоне Ýarym B ±10%% (dir0 = ПРЕФИКС [0; 15790] "
                            + "по ратификации A10.3-fix, №27)", label)
                    .isBetween(etalonKm * 0.9, etalonKm * 1.1);

            List<String> fullStopIds = full.stops().stream()
                    .map(GeometryFixture.StopPoint::stopId).toList();
            for (var sp : shortG.stops()) {
                assertThat(fullStopIds).as("стопы short ⊆ стопы full").contains(sp.stopId());
            }
            assertThat(shortG.stops().size() + cut.stopsDropped()).isEqualTo(full.stops().size());

            for (int i = 1; i < shortG.stops().size(); i++) {
                assertThat(shortG.stops().get(i).sMeters())
                        .as("%s: 0 инверсий s у стопов short (seq %d)", label, i)
                        .isGreaterThan(shortG.stops().get(i - 1).sMeters());
            }

            assertThat(RingCutout.isSimple(shortG, 500.0))
                    .as("%s: short проходит IsSimple строгим (префикс узла 16150/16867 не содержит)",
                            label)
                    .isTrue();
        }
    }

    @Test
    void activeCatalog25ContainsExactlyFullAndShortPrefix() {
        var topo = biz.ugur.busroutebackend.replay.RouteTopology
                .thereAndBack(FULL_1, FULL_0)
                .withVariants(java.util.List.of(short1().shortVariant(), short0().shortVariant()));
        var bank = new biz.ugur.busroutebackend.replay.core.HypothesisBank(
                biz.ugur.busroutebackend.replay.core.CoreConfig.defaults());
        bank.ensureBuilt(topo);
        var ids = bank.hypotheses().stream()
                .map(biz.ugur.busroutebackend.replay.core.HypothesisBank.Hypothesis::variantId)
                .sorted().toList();
        assertThat(ids)
                .as("активный банк 25 = РОВНО {full×dir, short-префикс×dir}; "
                        + "tail-candidate и superseded-v1 не заводятся (№27)")
                .containsExactly("25#d0", "25#d1", "25-short#d0", "25-short#d1");
        assertThat(ids).noneMatch(id -> id.contains("tail-candidate") || id.contains("superseded"));
        assertThat(short0TailInactiveCandidate().shortVariant().routeNumber())
                .as("суффикс-сборка сохранена неактивным кандидатом")
                .isEqualTo("25-short-tail-candidate");
        assertThat(short0TailInactiveCandidate().shortVariant().totalMeters() / 1000.0)
                .isBetween(17.0, 17.2);
    }

    @Test
    void supersededTrunkV1StillBuildsWithMark() {
        RingCutout.CutResult v1 = short0SupersededTrunkV1();
        assertThat(v1.shortVariant().routeNumber())
                .as("старая short-фикстура 25/0 (14.16 км) не удалена — помечена superseded, "
                        + "ссылка: ратификация s_turn=15790 (A10.3 Фаза Б, 2026-07-07)")
                .contains("superseded");
        assertThat(v1.shortVariant().totalMeters() / 1000.0).isBetween(14.0, 14.3);
    }
}
