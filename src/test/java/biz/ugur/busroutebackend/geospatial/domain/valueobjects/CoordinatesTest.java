package biz.ugur.busroutebackend.geospatial.domain.valueobjects;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class CoordinatesTest {

    @Nested
    class Factory {

        @Test
        void acceptsValidDoubleLatLon() {
            Coordinates c = Coordinates.of(37.95, 58.38);
            assertThat(c.getLatitudeAsDouble()).isEqualTo(37.95);
            assertThat(c.getLongitudeAsDouble()).isEqualTo(58.38);
        }

        @Test
        void roundsBigDecimalInputToSixDecimals() {
            Coordinates c = Coordinates.of(new BigDecimal("37.9500005"), new BigDecimal("58.3800005"));
            assertThat(c.getLatitude()).isEqualByComparingTo("37.950001");
            assertThat(c.getLongitude()).isEqualByComparingTo("58.380001");
        }

        @Test
        void rejectsNullInputs() {
            assertThatThrownBy(() -> Coordinates.of((Double) null, Double.valueOf(0.0)))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Coordinates.of((BigDecimal) null, BigDecimal.ZERO))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsLatitudeOutsideGlobalBounds() {
            assertThatThrownBy(() -> Coordinates.of(91.0, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Coordinates.of(-90.1, 0.0))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void rejectsLongitudeOutsideGlobalBounds() {
            assertThatThrownBy(() -> Coordinates.of(0.0, 180.01))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Coordinates.of(0.0, -180.01))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ArrayAndGeoJson {

        @Test
        void fromArrayExpectsLatLonOrder() {
            Coordinates c = Coordinates.fromArray(new double[]{37.95, 58.38});
            assertThat(c.getLatitudeAsDouble()).isEqualTo(37.95);
            assertThat(c.getLongitudeAsDouble()).isEqualTo(58.38);
        }

        @Test
        void fromGeoJsonExpectsLonLatOrder() {
            Coordinates c = Coordinates.fromGeoJson(new double[]{58.38, 37.95});
            assertThat(c.getLatitudeAsDouble()).isEqualTo(37.95);
            assertThat(c.getLongitudeAsDouble()).isEqualTo(58.38);
        }

        @Test
        void toArrayIsLatLon() {
            Coordinates c = Coordinates.of(37.95, 58.38);
            assertThat(c.toArray()).containsExactly(37.95, 58.38);
        }

        @Test
        void toGeoJsonIsLonLat() {
            Coordinates c = Coordinates.of(37.95, 58.38);
            assertThat(c.toGeoJson()).containsExactly(58.38, 37.95);
        }

        @Test
        void rejectsWrongArrayLength() {
            assertThatThrownBy(() -> Coordinates.fromArray(new double[]{1.0}))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Coordinates.fromArray(null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Wkt {

        @Test
        void roundTripsThroughWkt() {
            Coordinates original = Coordinates.of(37.95, 58.38);
            Coordinates parsed = Coordinates.fromWKT(original.toWKT());
            assertThat(parsed.getLatitudeAsDouble()).isCloseTo(37.95, within(0.0001));
            assertThat(parsed.getLongitudeAsDouble()).isCloseTo(58.38, within(0.0001));
        }

        @Test
        void rejectsInvalidWkt() {
            assertThatThrownBy(() -> Coordinates.fromWKT("")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Coordinates.fromWKT("LINESTRING(0 0)")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> Coordinates.fromWKT("POINT(abc def)")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class Geometry {

        @Test
        void isSameLocationWithinDefaultTolerance() {
            Coordinates a = Coordinates.of(37.95, 58.38);
            Coordinates b = Coordinates.of(37.95001, 58.380001);
            assertThat(a.isSameLocation(b)).isTrue();
        }

        @Test
        void isNotSameLocationBeyondTolerance() {
            Coordinates a = Coordinates.of(37.95, 58.38);
            Coordinates b = Coordinates.of(37.96, 58.38);
            assertThat(a.isSameLocation(b)).isFalse();
        }

        @Test
        void offsetMovesNorthAndEast() {
            Coordinates origin = Coordinates.of(37.95, 58.38);
            Coordinates moved = origin.offset(111320.0, 0.0);
            assertThat(moved.getLatitudeAsDouble()).isCloseTo(38.95, within(0.01));
        }

        @Test
        void bearingToEastIsNinetyDegrees() {
            Coordinates from = Coordinates.of(0.0, 0.0);
            Coordinates to = Coordinates.of(0.0, 1.0);
            assertThat(from.bearingTo(to)).isCloseTo(90.0, within(0.1));
        }
    }

    @Nested
    class Equality {

        @Test
        void equalCoordinatesHaveEqualHashCode() {
            Coordinates a = Coordinates.of(37.95, 58.38);
            Coordinates b = Coordinates.of(37.95, 58.38);
            assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
        }

        @Test
        void differentCoordinatesNotEqual() {
            assertThat(Coordinates.of(37.95, 58.38)).isNotEqualTo(Coordinates.of(37.96, 58.38));
        }
    }
}
