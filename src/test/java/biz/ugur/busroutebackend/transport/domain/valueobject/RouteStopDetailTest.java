package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class RouteStopDetailTest {

    private RouteStopDetail sample(Integer travel, Integer distance, Boolean major) {
        return new RouteStopDetail(
                "stop-1", "Center", "C1", 1, 0,
                travel, distance,
                BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.35),
                major
        );
    }

    @Test
    void gettersExposeAllFields() {
        RouteStopDetail detail = sample(5, 1500, true);

        assertThat(detail.getStopId()).isEqualTo("stop-1");
        assertThat(detail.getStopName()).isEqualTo("Center");
        assertThat(detail.getStopCode()).isEqualTo("C1");
        assertThat(detail.getSequence()).isEqualTo(1);
        assertThat(detail.getDirection()).isZero();
        assertThat(detail.getEstimatedTravelTimeMinutes()).isEqualTo(5);
        assertThat(detail.getDistanceFromStartMeters()).isEqualTo(1500);
    }

    @Test
    void travelTimeMinutesReturnsZeroWhenNull() {
        assertThat(sample(null, 1500, false).getTravelTimeMinutes()).isZero();
        assertThat(sample(7, 1500, false).getTravelTimeMinutes()).isEqualTo(7);
    }

    @Test
    void distanceMetersReturnsZeroWhenNull() {
        assertThat(sample(1, null, false).getDistanceMeters()).isZero();
        assertThat(sample(1, 2500, false).getDistanceMeters()).isEqualTo(2500);
    }

    @Test
    void distanceKmConvertsMetersCorrectly() {
        assertThat(sample(1, 2500, false).getDistanceKm()).isEqualTo(2.5);
        assertThat(sample(1, null, false).getDistanceKm()).isZero();
    }

    @Test
    void isMajorStopHandlesNullAsFalse() {
        assertThat(sample(1, 100, null).isMajorStop()).isFalse();
        assertThat(sample(1, 100, false).isMajorStop()).isFalse();
        assertThat(sample(1, 100, true).isMajorStop()).isTrue();
    }

    @Test
    void equalsAndHashCodeBasedOnAllFields() {
        RouteStopDetail a = sample(5, 1500, true);
        RouteStopDetail b = sample(5, 1500, true);
        RouteStopDetail differ = sample(6, 1500, true);

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(differ);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("string");
    }

    @Test
    void toStringContainsKeyFields() {
        String s = sample(5, 1500, true).toString();

        assertThat(s).contains("stop-1", "Center", "C1");
    }
}
