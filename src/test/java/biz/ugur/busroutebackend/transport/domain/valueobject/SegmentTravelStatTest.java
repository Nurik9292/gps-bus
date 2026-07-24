package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class SegmentTravelStatTest {

    private static final String ROUTE = "160";
    private static final int DIRECTION = 0;
    private static final String FROM = "stop-A";
    private static final String TO = "stop-B";
    private static final int HOUR = 8;
    private static final boolean WEEKEND = false;

    @Nested
    class InitialFactory {

        @Test
        void zeroSampleCount() {
            SegmentTravelStat stat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND);

            assertThat(stat.getSampleCount()).isZero();
            assertThat(stat.getAvgTravelSeconds()).isZero();
            assertThat(stat.getLastObservedAt()).isNull();
        }

        @Test
        void preservesKeyFields() {
            SegmentTravelStat stat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND);

            assertThat(stat.getRouteNumber()).isEqualTo(ROUTE);
            assertThat(stat.getDirection()).isEqualTo(DIRECTION);
            assertThat(stat.getFromStopId()).isEqualTo(FROM);
            assertThat(stat.getToStopId()).isEqualTo(TO);
            assertThat(stat.getHourOfDay()).isEqualTo(HOUR);
            assertThat(stat.isWeekend()).isFalse();
        }
    }

    @Nested
    class WithNewSample {

        @Test
        void firstSampleSetsAvgAndCount() {
            SegmentTravelStat stat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND);
            Instant observedAt = Instant.parse("2026-04-27T10:00:00Z");

            SegmentTravelStat updated = stat.withNewSample(120.0, observedAt);

            assertThat(updated.getSampleCount()).isEqualTo(1);
            assertThat(updated.getAvgTravelSeconds()).isEqualTo(120.0);
            assertThat(updated.getLastObservedAt()).isEqualTo(observedAt);
        }

        @Test
        void rollingAverageOverMultipleSamples() {
            SegmentTravelStat stat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND)
                    .withNewSample(100.0, Instant.now())
                    .withNewSample(200.0, Instant.now())
                    .withNewSample(150.0, Instant.now());

            assertThat(stat.getSampleCount()).isEqualTo(3);
            assertThat(stat.getAvgTravelSeconds()).isCloseTo(150.0, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        void preservesKeyFieldsAcrossSamples() {
            SegmentTravelStat updated = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND)
                    .withNewSample(60.0, Instant.now())
                    .withNewSample(180.0, Instant.now());

            assertThat(updated.getRouteNumber()).isEqualTo(ROUTE);
            assertThat(updated.getDirection()).isEqualTo(DIRECTION);
            assertThat(updated.getFromStopId()).isEqualTo(FROM);
            assertThat(updated.getToStopId()).isEqualTo(TO);
            assertThat(updated.getHourOfDay()).isEqualTo(HOUR);
            assertThat(updated.isWeekend()).isFalse();
        }

        @Test
        void incrementalConvergesToTrueAverage() {
            SegmentTravelStat stat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND);
            double[] samples = {100.0, 110.0, 90.0, 105.0, 95.0, 120.0, 80.0, 100.0};
            for (double s : samples) {
                stat = stat.withNewSample(s, Instant.now());
            }

            double expectedAvg = 100.0;
            assertThat(stat.getSampleCount()).isEqualTo(samples.length);
            assertThat(stat.getAvgTravelSeconds()).isCloseTo(expectedAvg, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        void zeroSampleAccepted() {
            SegmentTravelStat updated = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND)
                    .withNewSample(0.0, Instant.now());

            assertThat(updated.getSampleCount()).isEqualTo(1);
            assertThat(updated.getAvgTravelSeconds()).isZero();
        }

        @Test
        void updatedLastObservedAtTracksLatestSample() {
            Instant first = Instant.parse("2026-04-27T08:00:00Z");
            Instant second = Instant.parse("2026-04-27T10:00:00Z");

            SegmentTravelStat stat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, WEEKEND)
                    .withNewSample(100.0, first)
                    .withNewSample(120.0, second);

            assertThat(stat.getLastObservedAt()).isEqualTo(second);
        }
    }

    @Nested
    class WeekendVsWeekday {

        @Test
        void weekendFlagPreservedOnInitial() {
            SegmentTravelStat weekendStat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, true);
            SegmentTravelStat weekdayStat = SegmentTravelStat.initial("route-legacy-1", ROUTE, DIRECTION, FROM, TO, HOUR, false);

            assertThat(weekendStat.isWeekend()).isTrue();
            assertThat(weekdayStat.isWeekend()).isFalse();
        }
    }
}
