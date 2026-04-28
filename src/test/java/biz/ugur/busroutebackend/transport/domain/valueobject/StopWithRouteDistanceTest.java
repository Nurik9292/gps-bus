package biz.ugur.busroutebackend.transport.domain.valueobject;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StopWithRouteDistanceTest {

    @Test
    void builderProducesObjectWithAllFields() {
        StopWithRouteDistance stop = StopWithRouteDistance.builder()
                .stopId("stop-1")
                .stopName("Center")
                .latitude(37.95)
                .longitude(58.35)
                .stopSequence(3)
                .distanceOnRouteMeters(1500.0)
                .directDistanceMeters(900.0)
                .direction(0)
                .build();

        assertThat(stop.getStopId()).isEqualTo("stop-1");
        assertThat(stop.getStopName()).isEqualTo("Center");
        assertThat(stop.getLatitude()).isEqualTo(37.95);
        assertThat(stop.getLongitude()).isEqualTo(58.35);
        assertThat(stop.getStopSequence()).isEqualTo(3);
        assertThat(stop.getDistanceOnRouteMeters()).isEqualTo(1500.0);
        assertThat(stop.getDirectDistanceMeters()).isEqualTo(900.0);
        assertThat(stop.getDirection()).isZero();
    }

    @Test
    void hasRouteDistanceTrueWhenPositive() {
        StopWithRouteDistance withRoute = StopWithRouteDistance.builder()
                .distanceOnRouteMeters(500.0)
                .build();

        assertThat(withRoute.hasRouteDistance()).isTrue();
    }

    @Test
    void hasRouteDistanceFalseWhenNullOrZero() {
        StopWithRouteDistance noRoute = StopWithRouteDistance.builder().build();
        StopWithRouteDistance zero = StopWithRouteDistance.builder()
                .distanceOnRouteMeters(0.0)
                .build();

        assertThat(noRoute.hasRouteDistance()).isFalse();
        assertThat(zero.hasRouteDistance()).isFalse();
    }

    @Test
    void effectiveDistanceUsesRouteDistanceWhenAvailable() {
        StopWithRouteDistance stop = StopWithRouteDistance.builder()
                .distanceOnRouteMeters(1500.0)
                .directDistanceMeters(900.0)
                .build();

        assertThat(stop.getEffectiveDistance(1.4)).isEqualTo(1500.0);
    }

    @Test
    void effectiveDistanceFallsBackToDirectMultipliedByCorrection() {
        StopWithRouteDistance stop = StopWithRouteDistance.builder()
                .directDistanceMeters(1000.0)
                .build();

        assertThat(stop.getEffectiveDistance(1.4)).isEqualTo(1400.0);
    }

    @Test
    void effectiveDistanceReturnsZeroWhenBothMissing() {
        StopWithRouteDistance stop = StopWithRouteDistance.builder().build();

        assertThat(stop.getEffectiveDistance(1.4)).isZero();
    }

    @Test
    void equalsAndHashCodeBasedOnAllFields() {
        StopWithRouteDistance a = StopWithRouteDistance.builder()
                .stopId("s1").stopSequence(1).build();
        StopWithRouteDistance b = StopWithRouteDistance.builder()
                .stopId("s1").stopSequence(1).build();
        StopWithRouteDistance c = StopWithRouteDistance.builder()
                .stopId("s1").stopSequence(2).build();

        assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        assertThat(a).isNotEqualTo(c);
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("not-a-stop");
    }

    @Test
    void toStringContainsKeyFields() {
        StopWithRouteDistance stop = StopWithRouteDistance.builder()
                .stopId("s1").stopName("Center").build();

        assertThat(stop.toString()).contains("s1", "Center");
    }
}
