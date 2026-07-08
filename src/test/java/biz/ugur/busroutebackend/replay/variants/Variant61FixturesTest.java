package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.replay.GeometryFixture;
import biz.ugur.busroutebackend.replay.RouteTopology;
import biz.ugur.busroutebackend.replay.core.CoreConfig;
import biz.ugur.busroutebackend.replay.core.HypothesisBank;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Variant61FixturesTest {

    static final GeometryFixture FULL_0 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir0.json");
    static final GeometryFixture FULL_1 =
            GeometryFixture.loadClasspath("/fixtures/geometry/route-61-dir1.json");

    static final double GOKJE_S_TURN_61_0_RATIFIED_M = 9844.0;
    static final GeometryFixture.TerminalZone GOKJE_TERMINAL_ZONE_RATIFIED =
            new GeometryFixture.TerminalZone(37.981209, 58.232796, 1050.0);
    static final double GOKJE_S_TURN_61_1_RATIFIED_M = 16990.0;
    static final double[] GOKJE_BN_STOP = {37.98154696278676, 58.23298394680023};
    static final double RATIFIED_LOOP_M = 19687.0;

    public static RingCutout.CutResult gokje0() {
        RingCutout.CutResult cut = RingCutout.prefixToS(FULL_0, GOKJE_S_TURN_61_0_RATIFIED_M, "61-gokje");
        return new RingCutout.CutResult(
                cut.shortVariant().withTerminalZone(GOKJE_TERMINAL_ZONE_RATIFIED),
                cut.trunkStartS(), cut.trunkEndS(), cut.stopsDropped());
    }

    public static RingCutout.CutResult gokjeTail1() {
        return RingCutout.suffixFromS(FULL_1, GOKJE_S_TURN_61_1_RATIFIED_M, "61-gokje-tail");
    }

    static RingCutout.CutResult parkedShortD1BboxCandidate() {
        return RingCutout.trunkOutsideRingZone(FULL_1,
                new RingCutout.Bbox(58.145, 58.215, 38.038, 38.09), "61-short-d1-parked-candidate");
    }

    @Test
    void gokjePairDerivedFromRatifiedSheetPointsNotBbox() {
        GeometryFixture prefix = gokje0().shortVariant();
        GeometryFixture tail = gokjeTail1().shortVariant();

        assertThat(prefix.totalMeters())
                .as("префикс 61-gokje#d0 = Γ_61/0 [0; 9 844] (Р-8, лист 61ug(2))")
                .isCloseTo(GOKJE_S_TURN_61_0_RATIFIED_M, org.assertj.core.data.Offset.offset(30.0));
        assertThat(tail.totalMeters())
                .as("хвост 61-gokje-tail#d1 = Γ_61/1 [16 990; 26 821], L_v = 9 831")
                .isCloseTo(26821.0 - GOKJE_S_TURN_61_1_RATIFIED_M, org.assertj.core.data.Offset.offset(30.0));

        double[] prefixEnd = prefix.pointAtS(prefix.totalMeters());
        double dEndToGokje = GeometryFixture.haversineMeters(
                prefixEnd[0], prefixEnd[1], GOKJE_BN_STOP[0], GOKJE_BN_STOP[1]);
        assertThat(dEndToGokje)
                .as("конец префикса до стоп-точки Gökje b/n: замер Р-8 41 м + допуск 20")
                .isLessThanOrEqualTo(41.0 + 20.0);

        double[] tailStart = tail.pointAtS(0);
        double dStartToGokje = GeometryFixture.haversineMeters(
                tailStart[0], tailStart[1], GOKJE_BN_STOP[0], GOKJE_BN_STOP[1]);
        assertThat(dStartToGokje)
                .as("начало хвоста до стоп-точки Gökje b/n: замер Р-8 60 м + допуск 20")
                .isLessThanOrEqualTo(60.0 + 20.0);

        double loop = prefix.totalMeters() + tail.totalMeters();
        assertThat(loop)
                .as("петля Gurtly↔Gökje = 19 687 м ± 1% (−1.6% от приказных 20 км)")
                .isBetween(RATIFIED_LOOP_M * 0.99, RATIFIED_LOOP_M * 1.01);
    }

    @Test
    void bboxBoundaryIsNotACutSourceForActive61Variants() {
        double bboxPrefixLen = 18483.0;
        for (GeometryFixture active : List.of(gokje0().shortVariant(), gokjeTail1().shortVariant())) {
            assertThat(Math.abs(active.totalMeters() - bboxPrefixLen))
                    .as("активная 61-фикстура %s не является bbox-дериватом (№26(в)/№27; " +
                            "RETIRED 61-short#d0 в код не заводится)", active.routeNumber())
                    .isGreaterThan(1000.0);
        }
    }

    @Test
    void parkedShortD1StaysOutOfBankAndNoTwinEndsAtParentTerminal() {
        GeometryFixture parked = parkedShortD1BboxCandidate().shortVariant();
        assertThat(parked.routeNumber()).isEqualTo("61-short-d1-parked-candidate");

        RouteTopology target = RouteTopology.thereAndBack(FULL_0, FULL_1)
                .withVariants(List.of(gokje0().shortVariant(), gokjeTail1().shortVariant()));
        HypothesisBank bank = new HypothesisBank(CoreConfig.defaults());
        bank.ensureBuilt(target);

        List<String> ids = bank.hypotheses().stream()
                .map(HypothesisBank.Hypothesis::variantId).sorted().toList();
        assertThat(ids)
                .as("целевой состав банка 61 (Фаза 2 B12.1) — parked/retired вне банка")
                .containsExactly("61#d0", "61#d1", "61-gokje#d0", "61-gokje-tail#d1");
        assertThat(ids).noneMatch(id -> id.contains("candidate") || id.contains("parked"));

        for (HypothesisBank.Hypothesis h : bank.hypotheses()) {
            if (!h.variantId().contains("gokje")) continue;
            GeometryFixture parent = h.direction() == 0 ? FULL_0 : FULL_1;
            double[] vEnd = h.geom().pointAtS(h.geom().totalMeters());
            double[] pEnd = parent.pointAtS(parent.totalMeters());
            boolean endsAtParentTerminal = GeometryFixture.haversineMeters(
                    vEnd[0], vEnd[1], pEnd[0], pEnd[1]) <= 150.0;
            if (!endsAtParentTerminal) continue;
            double[] vStart = h.geom().pointAtS(0);
            double dStartToGokje = GeometryFixture.haversineMeters(
                    vStart[0], vStart[1], GOKJE_BN_STOP[0], GOKJE_BN_STOP[1]);
            assertThat(dStartToGokje)
                    .as("№27 (guard, ратиф. 08.07): конец на терминале родителя допустим ⇔ старт " +
                            "гипотезы = ратифицированная точка разворота в теле из реестра " +
                            "(для 61 = Gökje; прецедент 25-short-tail#d1)",
                            h.variantId())
                    .isLessThanOrEqualTo(150.0);
        }
    }
}
