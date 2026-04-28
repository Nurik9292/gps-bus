package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteStopsDataTest {

    private RouteStopDetail stop(String id, int sequence, int direction) {
        return new RouteStopDetail(
                id, "Stop " + id, "C-" + id, sequence, direction,
                5, 1500, BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.35), false
        );
    }

    @Test
    void defaultConstructorProducesEmptyLists() {
        RouteStopsData data = new RouteStopsData();

        assertThat(data.getForwardStops()).isEmpty();
        assertThat(data.getBackwardStops()).isEmpty();
        assertThat(data.hasStops()).isFalse();
        assertThat(data.getTotalStopsCount()).isZero();
    }

    @Test
    void countersReturnSizesCorrectly() {
        RouteStopsData data = new RouteStopsData(
                List.of(stop("a", 1, 0), stop("b", 2, 0)),
                List.of(stop("c", 1, 1))
        );

        assertThat(data.getForwardStopsCount()).isEqualTo(2);
        assertThat(data.getBackwardStopsCount()).isEqualTo(1);
        assertThat(data.getTotalStopsCount()).isEqualTo(3);
    }

    @Test
    void hasStopsTrueWhenAtLeastOneList() {
        RouteStopsData onlyForward = new RouteStopsData(List.of(stop("a", 1, 0)), List.of());
        RouteStopsData onlyBackward = new RouteStopsData(List.of(), List.of(stop("a", 1, 1)));

        assertThat(onlyForward.hasStops()).isTrue();
        assertThat(onlyForward.hasForwardStops()).isTrue();
        assertThat(onlyForward.hasBackwardStops()).isFalse();

        assertThat(onlyBackward.hasStops()).isTrue();
        assertThat(onlyBackward.hasForwardStops()).isFalse();
        assertThat(onlyBackward.hasBackwardStops()).isTrue();
    }

    @Test
    void nullListsAreReplacedByEmpty() {
        RouteStopsData data = new RouteStopsData(null, null);

        assertThat(data.getForwardStops()).isEmpty();
        assertThat(data.getBackwardStops()).isEmpty();
    }

    @Test
    void equalsAndHashCodeBasedOnLists() {
        RouteStopsData a = new RouteStopsData(List.of(stop("x", 1, 0)), List.of());
        RouteStopsData b = new RouteStopsData(List.of(stop("x", 1, 0)), List.of());
        RouteStopsData c = new RouteStopsData(List.of(stop("y", 1, 0)), List.of());

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    void toStringRendersFields() {
        RouteStopsData data = new RouteStopsData(List.of(stop("x", 1, 0)), List.of());

        assertThat(data.toString()).contains("forwardStops", "backwardStops");
    }
}
