package biz.ugur.busroutebackend.replay.variants;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class Case1474LoopShortcutRegressionTest {

    @Test
    void loopSkipReattachesWithinFortySeconds() throws Exception {
        var report = ShortcutReplaySupport.replay("16",
                "/fixtures/replay/case1474-loop-20260720.jsonl",
                Instant.parse("2026-07-20T07:14:00Z"),
                Instant.parse("2026-07-20T07:22:00Z"),
                250.0);

        assertThat(report.maxContinuousSecondsAbove())
                .as("срезка восточной петли: расхождение >250 м не дольше 40 c "
                        + "(жило: ~90 c прохода петли маркером)")
                .isLessThanOrEqualTo(40);
        assertThat(report.directionChanges())
                .as("перескок не меняет направление")
                .isLessThanOrEqualTo(1);
        assertThat(report.tripIncrements())
                .as("перескок не рождает trip++ сверх штатных границ окна")
                .isLessThanOrEqualTo(1);
    }
}
