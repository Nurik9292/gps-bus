package biz.ugur.busroutebackend.replay.variants;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class Case1633FoldShortcutRegressionTest {

    @Test
    void foldSkipDoesNotLagKilometers() throws Exception {
        var report = ShortcutReplaySupport.replay("32",
                "/fixtures/replay/case1633-fold-20260720.jsonl",
                Instant.parse("2026-07-20T07:36:00Z"),
                Instant.parse("2026-07-20T07:44:00Z"),
                400.0);

        assertThat(report.maxDivergenceMeters())
                .as("срезка заезда-складки: маркер не отстаёт километрами "
                        + "(жило: до 1.8 км позади борта)")
                .isLessThan(900.0);
        assertThat(report.maxContinuousSecondsAbove())
                .as("расхождение >400 м не дольше 40 c (жило: ~160 c)")
                .isLessThanOrEqualTo(40);
        assertThat(report.directionChanges())
                .as("перескок не меняет направление")
                .isZero();
        assertThat(report.tripIncrements())
                .as("перескок не рождает trip++")
                .isZero();
        assertThat(report.shortcutJumps())
                .as("перескок ровно один — без прыгающего поведения")
                .isEqualTo(1);
    }
}
