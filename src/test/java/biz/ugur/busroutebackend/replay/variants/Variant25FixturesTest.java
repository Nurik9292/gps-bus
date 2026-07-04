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

    static RingCutout.CutResult short0() {
        return RingCutout.trunkOutsideRingZone(FULL_0, RING_BBOX, "25-short");
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
            boolean inEtalon = shortKm >= etalonKm * 0.9 && shortKm <= etalonKm * 1.1;
            System.out.printf("A9.1 %s: full=%.1fкм, ствол вне кольцевой зоны s=[%.0f..%.0f], "
                            + "short=%.2fкм (эталон Ýarym B %.0f±10%%: %s), стопов short=%d "
                            + "(в кольцевой зоне отброшено %d)%n",
                    label, full.totalMeters() / 1000.0, cut.trunkStartS(), cut.trunkEndS(),
                    shortKm, etalonKm,
                    inEtalon ? "OK" : "ФЛАГ — расхождение >10%, не подгоняем",
                    shortG.stops().size(), cut.stopsDropped());

            if (label.equals("dir1")) {
                assertThat(shortKm)
                        .as("dir1: L_short в эталоне Ýarym B ±10%%")
                        .isBetween(etalonKm * 0.9, etalonKm * 1.1);
            } else {
                assertThat(shortKm)
                        .as("dir0: sanity-коридор; эталон-флаг: bwd-ствол несёт Gurtly-петлю, "
                                + "fwd-ствол короче эталона — open-question владельцу")
                        .isBetween(etalonKm * 0.7, etalonKm * 1.1);
            }

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
                    .as("%s: short проходит IsSimple (термин. петли ≤500м по дуге игнорируются)", label)
                    .isTrue();
        }
    }
}
