package biz.ugur.busroutebackend.geospatial.domain.constants;

import java.math.BigDecimal;

public final class TurkmenistanBounds {

    private TurkmenistanBounds() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }


    public static final BigDecimal MIN_LATITUDE = new BigDecimal("35.00");

    public static final BigDecimal MAX_LATITUDE = new BigDecimal("43.00");

    public static final BigDecimal MIN_LONGITUDE = new BigDecimal("52.00");

    public static final BigDecimal MAX_LONGITUDE = new BigDecimal("67.00");


    public static final BigDecimal STRICT_MIN_LATITUDE = new BigDecimal("35.10");

    public static final BigDecimal STRICT_MAX_LATITUDE = new BigDecimal("42.80");

    public static final BigDecimal STRICT_MIN_LONGITUDE = new BigDecimal("52.50");

    public static final BigDecimal STRICT_MAX_LONGITUDE = new BigDecimal("66.70");


    public static final class Cities {

        private Cities() {
            throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
        }

        public static final BigDecimal ASHGABAT_LAT = new BigDecimal("37.9601");

        public static final BigDecimal ASHGABAT_LON = new BigDecimal("58.3261");

        public static final BigDecimal TURKMENABAT_LAT = new BigDecimal("39.0936");

        public static final BigDecimal TURKMENABAT_LON = new BigDecimal("63.5787");

        public static final BigDecimal TURKMENBASHI_LAT = new BigDecimal("40.0225");

        public static final BigDecimal TURKMENBASHI_LON = new BigDecimal("52.9550");

        public static final BigDecimal MARY_LAT = new BigDecimal("37.5942");

        public static final BigDecimal MARY_LON = new BigDecimal("61.8306");

        public static final BigDecimal BALKANABAT_LAT = new BigDecimal("39.5108");

        public static final BigDecimal BALKANABAT_LON = new BigDecimal("54.3671");

        public static final BigDecimal DASHOGUZ_LAT = new BigDecimal("41.8369");

        public static final BigDecimal DASHOGUZ_LON = new BigDecimal("59.9658");
    }


    public static boolean isWithinStandardBounds(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null &&
               latitude.compareTo(MIN_LATITUDE) >= 0 &&
               latitude.compareTo(MAX_LATITUDE) <= 0 &&
               longitude.compareTo(MIN_LONGITUDE) >= 0 &&
               longitude.compareTo(MAX_LONGITUDE) <= 0;
    }

    public static boolean isWithinStrictBounds(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null && longitude != null &&
               latitude.compareTo(STRICT_MIN_LATITUDE) >= 0 &&
               latitude.compareTo(STRICT_MAX_LATITUDE) <= 0 &&
               longitude.compareTo(STRICT_MIN_LONGITUDE) >= 0 &&
               longitude.compareTo(STRICT_MAX_LONGITUDE) <= 0;
    }

    public static boolean isWithinStandardBounds(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return isWithinStandardBounds(
            BigDecimal.valueOf(latitude),
            BigDecimal.valueOf(longitude)
        );
    }

    public static boolean isWithinStrictBounds(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return false;
        }
        return isWithinStrictBounds(
            BigDecimal.valueOf(latitude),
            BigDecimal.valueOf(longitude)
        );
    }

    public static void validateStandardBounds(BigDecimal latitude, BigDecimal longitude) {
        if (!isWithinStandardBounds(latitude, longitude)) {
            throw new IllegalArgumentException(
                String.format(
                    "Coordinates (%.6f, %.6f) are outside Turkmenistan standard bounds [%.2f-%.2f, %.2f-%.2f]",
                    latitude.doubleValue(), longitude.doubleValue(),
                    MIN_LATITUDE, MAX_LATITUDE,
                    MIN_LONGITUDE, MAX_LONGITUDE
                )
            );
        }
    }

    public static void validateStrictBounds(BigDecimal latitude, BigDecimal longitude) {
        if (!isWithinStrictBounds(latitude, longitude)) {
            throw new IllegalArgumentException(
                String.format(
                    "Coordinates (%.6f, %.6f) are outside Turkmenistan strict bounds [%.2f-%.2f, %.2f-%.2f]",
                    latitude.doubleValue(), longitude.doubleValue(),
                    STRICT_MIN_LATITUDE, STRICT_MAX_LATITUDE,
                    STRICT_MIN_LONGITUDE, STRICT_MAX_LONGITUDE
                )
            );
        }
    }


    public static String getDocumentation() {
        return """
            Turkmenistan Geographic Bounds:

            Standard Bounds (for vehicle tracking):
            - Latitude:  %.2f° N to %.2f° N
            - Longitude: %.2f° E to %.2f° E
            - Area: ~%.0f km × ~%.0f km

            Strict Bounds (for fixed infrastructure):
            - Latitude:  %.2f° N to %.2f° N
            - Longitude: %.2f° E to %.2f° E

            Major Cities:
            - Ashgabat (capital): %.4f° N, %.4f° E
            - Turkmenabat: %.4f° N, %.4f° E
            - Turkmenbashi: %.4f° N, %.4f° E

            Usage:
            - Use standard bounds for real-time vehicle tracking
            - Use strict bounds for bus stops and permanent infrastructure
            - Test coordinates against major cities for validation
            """.formatted(
                MIN_LATITUDE, MAX_LATITUDE,
                MIN_LONGITUDE, MAX_LONGITUDE,
                (MAX_LATITUDE.doubleValue() - MIN_LATITUDE.doubleValue()) * 111.0, // rough km
                (MAX_LONGITUDE.doubleValue() - MIN_LONGITUDE.doubleValue()) * 85.0, // rough km at 40°N
                STRICT_MIN_LATITUDE, STRICT_MAX_LATITUDE,
                STRICT_MIN_LONGITUDE, STRICT_MAX_LONGITUDE,
                Cities.ASHGABAT_LAT, Cities.ASHGABAT_LON,
                Cities.TURKMENABAT_LAT, Cities.TURKMENABAT_LON,
                Cities.TURKMENBASHI_LAT, Cities.TURKMENBASHI_LON
            );
    }

    public static BigDecimal[] getCenterPoint() {
        BigDecimal centerLat = MIN_LATITUDE.add(MAX_LATITUDE).divide(BigDecimal.valueOf(2));
        BigDecimal centerLon = MIN_LONGITUDE.add(MAX_LONGITUDE).divide(BigDecimal.valueOf(2));
        return new BigDecimal[]{centerLat, centerLon};
    }
}
