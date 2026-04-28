package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SimpleValueObjectsTest {

    @Nested
    class StopRouteDetailTest {

        private StopRouteDetail sample() {
            return new StopRouteDetail("r-1", "Main Line", "15", 0, 25, 10000);
        }

        @Test
        void gettersExposeAllFields() {
            StopRouteDetail d = sample();
            assertThat(d.getRouteId()).isEqualTo("r-1");
            assertThat(d.getRouteName()).isEqualTo("Main Line");
            assertThat(d.getRouteNumber()).isEqualTo("15");
            assertThat(d.getDirection()).isZero();
            assertThat(d.getEstimatedTravelTimeMinutes()).isEqualTo(25);
            assertThat(d.getDistanceFromStartMeters()).isEqualTo(10000);
        }

        @Test
        void equalsAndHashCodeOnAllFields() {
            StopRouteDetail a = sample();
            StopRouteDetail b = sample();
            StopRouteDetail c = new StopRouteDetail("r-1", "Main Line", "16", 0, 25, 10000);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
            assertThat(a).isNotEqualTo(null);
            assertThat(a).isNotEqualTo("string");
        }

        @Test
        void toStringContainsKeyFields() {
            assertThat(sample().toString()).contains("r-1", "Main Line", "15");
        }
    }

    @Nested
    class VehiclePositionTests {

        @Test
        void constructorAcceptsLatLonAndDefaultsNullsToZero() {
            VehiclePosition p = new VehiclePosition(37.95, 58.35, null, null);

            assertThat(p.getLatitude()).isEqualTo(37.95);
            assertThat(p.getLongitude()).isEqualTo(58.35);
            assertThat(p.getSpeedKmh()).isZero();
            assertThat(p.getIsInMotion()).isFalse();
        }

        @Test
        void constructorRejectsNullCoordinates() {
            assertThatThrownBy(() -> new VehiclePosition(null, 58.35, 0.0, false))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new VehiclePosition(37.95, null, 0.0, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void factoryOfBuildsFromCoordinates() {
            Coordinates c = Coordinates.of(37.95, 58.35);
            VehiclePosition p = VehiclePosition.of(c, 30.0, true);

            assertThat(p.getCoordinates()).isEqualTo(c);
            assertThat(p.getSpeedKmh()).isEqualTo(30.0);
            assertThat(p.getIsInMotion()).isTrue();
        }

        @Test
        void factoryOfRejectsNullCoordinates() {
            assertThatThrownBy(() -> VehiclePosition.of(null, 0.0, false))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void toStringRendersHumanReadable() {
            VehiclePosition p = new VehiclePosition(37.95, 58.35, 30.5, true);
            assertThat(p.toString()).contains("37.95", "58.35", "30.5");
        }
    }

    @Nested
    class StopDwellStatTest {

        @Test
        void initialProducesBaselineWithDefaultAvg() {
            StopDwellStat stat = StopDwellStat.initial("s1", "15", 0);

            assertThat(stat.getStopId()).isEqualTo("s1");
            assertThat(stat.getRouteNumber()).isEqualTo("15");
            assertThat(stat.getDirection()).isZero();
            assertThat(stat.getAvgDwellSeconds()).isEqualTo(15.0);
            assertThat(stat.getSampleCount()).isZero();
            assertThat(stat.getMinDwellSeconds()).isNull();
            assertThat(stat.getMaxDwellSeconds()).isNull();
        }

        @Test
        void withNewSampleSetsMinMaxAndUpdatesAvg() {
            StopDwellStat empty = StopDwellStat.initial("s1", "15", 0);
            Instant t = Instant.now();

            StopDwellStat withOne = empty.withNewSample(20.0, t);

            assertThat(withOne.getMinDwellSeconds()).isEqualTo(20.0);
            assertThat(withOne.getMaxDwellSeconds()).isEqualTo(20.0);
            assertThat(withOne.getSampleCount()).isEqualTo(1);
            assertThat(withOne.getLastDwellAt()).isEqualTo(t);
        }

        @Test
        void minMaxUpdateOnSubsequentSamples() {
            StopDwellStat stat = StopDwellStat.initial("s1", "15", 0)
                    .withNewSample(20.0, Instant.now())
                    .withNewSample(10.0, Instant.now())
                    .withNewSample(40.0, Instant.now());

            assertThat(stat.getMinDwellSeconds()).isEqualTo(10.0);
            assertThat(stat.getMaxDwellSeconds()).isEqualTo(40.0);
            assertThat(stat.getSampleCount()).isEqualTo(3);
        }
    }

    @Nested
    class RouteVehicleStatisticsTest {

        @Test
        void nullCountsDefaultToZero() {
            RouteVehicleStatistics s = new RouteVehicleStatistics(null, null, null);
            assertThat(s.getActiveVehiclesCount()).isZero();
            assertThat(s.getVehiclesInMotionCount()).isZero();
            assertThat(s.getVehiclesWithRecentPositionCount()).isZero();
            assertThat(s.hasActiveVehicles()).isFalse();
            assertThat(s.hasVehiclesInMotion()).isFalse();
            assertThat(s.hasRecentData()).isFalse();
        }

        @Test
        void hasActiveVehiclesTrueWhenPositive() {
            RouteVehicleStatistics s = new RouteVehicleStatistics(5L, 3L, 4L);
            assertThat(s.hasActiveVehicles()).isTrue();
            assertThat(s.hasVehiclesInMotion()).isTrue();
            assertThat(s.hasRecentData()).isTrue();
        }

        @Test
        void equalsHashCodeAndToString() {
            RouteVehicleStatistics a = new RouteVehicleStatistics(5L, 3L, 4L);
            RouteVehicleStatistics b = new RouteVehicleStatistics(5L, 3L, 4L);

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a.toString()).contains("5", "3", "4");
        }
    }

    @Nested
    class RouteStopInfoTest {

        @Test
        void requiredFieldsRejectNull() {
            assertThatThrownBy(() -> new RouteStopInfo(
                    null, "Center", "C1", 1, 0, 5, 1500,
                    BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.35), true))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> new RouteStopInfo(
                    "s1", null, "C1", 1, 0, 5, 1500,
                    BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.35), true))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> new RouteStopInfo(
                    "s1", "Center", "C1", 1, 0, 5, 1500,
                    null, BigDecimal.valueOf(58.35), true))
                    .isInstanceOf(NullPointerException.class);

            assertThatThrownBy(() -> new RouteStopInfo(
                    "s1", "Center", "C1", 1, 0, 5, 1500,
                    BigDecimal.valueOf(37.95), null, true))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void isMajorStopDefaultsToFalseWhenNull() {
            RouteStopInfo info = new RouteStopInfo(
                    "s1", "Center", "C1", 1, 0, 5, 1500,
                    BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.35), null);

            assertThat(info.getIsMajorStop()).isFalse();
        }

        @Test
        void allGettersExposed() {
            RouteStopInfo info = new RouteStopInfo(
                    "s1", "Center", "C1", 1, 0, 5, 1500,
                    BigDecimal.valueOf(37.95), BigDecimal.valueOf(58.35), true);

            assertThat(info.getStopId()).isEqualTo("s1");
            assertThat(info.getStopName()).isEqualTo("Center");
            assertThat(info.getStopCode()).isEqualTo("C1");
            assertThat(info.getSequence()).isEqualTo(1);
            assertThat(info.getDirection()).isZero();
            assertThat(info.getEstimatedTravelTimeMinutes()).isEqualTo(5);
            assertThat(info.getDistanceFromStartMeters()).isEqualTo(1500);
            assertThat(info.getLatitude()).isEqualTo(BigDecimal.valueOf(37.95));
            assertThat(info.getLongitude()).isEqualTo(BigDecimal.valueOf(58.35));
            assertThat(info.getIsMajorStop()).isTrue();
        }
    }

    @Nested
    class RouteAlternativeIdTest {

        @Test
        void generateProducesUniqueValue() {
            assertThat(RouteAlternativeId.generate().getValue()).isNotBlank();
            assertThat(RouteAlternativeId.generate()).isNotEqualTo(RouteAlternativeId.generate());
        }

        @Test
        void ofTrimsValueAndRejectsBlank() {
            assertThat(RouteAlternativeId.of("  abc  ").getValue()).isEqualTo("abc");
            assertThatThrownBy(() -> RouteAlternativeId.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RouteAlternativeId.of(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RouteAlternativeId.of("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void toStringReturnsValue() {
            assertThat(RouteAlternativeId.of("xyz").toString()).isEqualTo("xyz");
        }
    }

    @Nested
    class RouteAssignmentIdTest {

        @Test
        void generateProducesUniqueValue() {
            assertThat(RouteAssignmentId.generate().getValue()).isNotBlank();
            assertThat(RouteAssignmentId.generate()).isNotEqualTo(RouteAssignmentId.generate());
        }

        @Test
        void ofTrimsValueAndRejectsBlank() {
            assertThat(RouteAssignmentId.of("  abc  ").getValue()).isEqualTo("abc");
            assertThatThrownBy(() -> RouteAssignmentId.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RouteAssignmentId.of(""))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void toStringReturnsValue() {
            assertThat(RouteAssignmentId.of("xyz").toString()).isEqualTo("xyz");
        }
    }

    @Nested
    class RouteInAreaInfoTest {

        private RouteInAreaInfo sample(Integer direction) {
            return new RouteInAreaInfo("r-1", "15", "Main", "#FF0000",
                    direction, 37.95, 58.35, 1500.0);
        }

        @Test
        void rejectNullMandatoryFields() {
            assertThatThrownBy(() -> new RouteInAreaInfo(null, "15", "Main", "#000",
                    0, 37.95, 58.35, 1500.0))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new RouteInAreaInfo("r-1", null, "Main", "#000",
                    0, 37.95, 58.35, 1500.0))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void allowOptionalFieldsToBeNull() {
            RouteInAreaInfo info = new RouteInAreaInfo("r-1", "15", null, null, null, null, null, null);
            assertThat(info.getRouteName()).isNull();
            assertThat(info.getDirection()).isNull();
            assertThat(info.getDistanceToCenterMeters()).isNull();
        }

        @Test
        void directionFlagsHandleNullAndExpectedValues() {
            assertThat(sample(0).isForwardDirection()).isTrue();
            assertThat(sample(0).isBackwardDirection()).isFalse();
            assertThat(sample(1).isForwardDirection()).isFalse();
            assertThat(sample(1).isBackwardDirection()).isTrue();
            assertThat(sample(null).isForwardDirection()).isFalse();
            assertThat(sample(null).isBackwardDirection()).isFalse();
        }

        @Test
        void gettersExposeAllFields() {
            RouteInAreaInfo info = sample(0);
            assertThat(info.getRouteId()).isEqualTo("r-1");
            assertThat(info.getRouteNumber()).isEqualTo("15");
            assertThat(info.getRouteName()).isEqualTo("Main");
            assertThat(info.getRouteColor()).isEqualTo("#FF0000");
            assertThat(info.getDirection()).isZero();
            assertThat(info.getNearestPointLatitude()).isEqualTo(37.95);
            assertThat(info.getNearestPointLongitude()).isEqualTo(58.35);
            assertThat(info.getDistanceToCenterMeters()).isEqualTo(1500.0);
        }
    }

    @Nested
    class RouteStopIdTest {

        @Test
        void generateProducesUniqueValue() {
            RouteStopId a = RouteStopId.generate();
            RouteStopId b = RouteStopId.generate();
            assertThat(a.getValue()).isNotBlank();
            assertThat(a).isNotEqualTo(b);
        }

        @Test
        void ofTrimsValue() {
            RouteStopId id = RouteStopId.of("  abc  ");
            assertThat(id.getValue()).isEqualTo("abc");
        }

        @Test
        void rejectsNullOrEmpty() {
            assertThatThrownBy(() -> RouteStopId.of(null))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RouteStopId.of(""))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> RouteStopId.of("  "))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void toStringReturnsValue() {
            assertThat(RouteStopId.of("xyz").toString()).isEqualTo("xyz");
        }

        @Test
        void equalsAndHashCodeOnValue() {
            RouteStopId a = RouteStopId.of("abc");
            RouteStopId b = RouteStopId.of("abc");
            RouteStopId c = RouteStopId.of("xyz");

            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
            assertThat(a).isNotEqualTo(c);
        }
    }
}
