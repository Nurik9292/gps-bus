package biz.ugur.busroutebackend.replay.variants;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class Case1474LoopShortcutRegressionTest {

    @Test
    void shortcutChannelDoesNotInterfereWithLoopSkipClass() throws Exception {
        var report = ShortcutReplaySupport.replay("16",
                "/fixtures/replay/case1474-loop-20260720.jsonl",
                Instant.parse("2026-07-20T07:14:00Z"),
                Instant.parse("2026-07-20T07:22:00Z"),
                250.0);

        assertThat(report.shortcutJumps())
                .as("петлевой класс вне скоупа детектора (ратификация 23.07): "
                        + "у канала нет ранних кандидатов — он обязан молчать")
                .isZero();
        assertThat(report.maxContinuousSecondsAbove())
                .as("поведение не хуже базлайна реанкора (230 c)")
                .isLessThanOrEqualTo(250);
        assertThat(report.directionChanges())
                .as("направление в окне нетронуто")
                .isZero();
        assertThat(report.tripIncrements())
                .as("trip в окне нетронут")
                .isZero();
    }
}
