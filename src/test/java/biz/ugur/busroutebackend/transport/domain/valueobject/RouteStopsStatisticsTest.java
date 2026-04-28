package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RouteStopsStatisticsTest {

    @Test
    void gettersExposeAllFields() {
        RouteStopsStatistics stats = new RouteStopsStatistics(
                10L, 8L, 45, 40, 12000, 11500
        );

        assertThat(stats.getForwardStopsCount()).isEqualTo(10L);
        assertThat(stats.getBackwardStopsCount()).isEqualTo(8L);
        assertThat(stats.getForwardTotalTravelTime()).isEqualTo(45);
        assertThat(stats.getBackwardTotalTravelTime()).isEqualTo(40);
        assertThat(stats.getForwardTotalDistance()).isEqualTo(12000);
        assertThat(stats.getBackwardTotalDistance()).isEqualTo(11500);
    }

    @Test
    void equalsAndHashCodeBasedOnAllFields() {
        RouteStopsStatistics a = new RouteStopsStatistics(10L, 8L, 45, 40, 12000, 11500);
        RouteStopsStatistics b = new RouteStopsStatistics(10L, 8L, 45, 40, 12000, 11500);
        RouteStopsStatistics c = new RouteStopsStatistics(11L, 8L, 45, 40, 12000, 11500);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    void toStringContainsKeyFields() {
        RouteStopsStatistics stats = new RouteStopsStatistics(10L, 8L, 45, 40, 12000, 11500);

        assertThat(stats.toString())
                .contains("forwardStopsCount=10")
                .contains("backwardStopsCount=8")
                .contains("forwardTotalDistance=12000");
    }

    @Test
    void allowsNullFieldValues() {
        RouteStopsStatistics stats = new RouteStopsStatistics(null, null, null, null, null, null);

        assertThat(stats.getForwardStopsCount()).isNull();
        assertThat(stats.getBackwardTotalDistance()).isNull();
    }
}
