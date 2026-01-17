package biz.ugur.busroutebackend.geospatial.domain.constants;

public final class GeoConstants {

    private GeoConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }


    public static final double EARTH_RADIUS_METERS = 6_371_000.0;

    public static final int COORDINATE_DECIMAL_PLACES = 6;

    public static final int SRID_WGS84 = 4326;


    public static final double SIGNIFICANT_POSITION_CHANGE_METERS = 5.0;

    public static final double POSITION_TOLERANCE_METERS = 10.0;

    public static final double MAX_STOP_RELOCATION_METERS = 500.0;


    public static final double AVERAGE_WALKING_SPEED_KMH = 5.0;

    public static final double AVERAGE_WALKING_SPEED_M_PER_MIN =
        AVERAGE_WALKING_SPEED_KMH * 1000.0 / 60.0;

    public static final double AVERAGE_WALKING_SPEED_M_PER_SEC =
        AVERAGE_WALKING_SPEED_KMH * 1000.0 / 3600.0;

    public static final int MAX_WALKING_TIME_MINUTES = 20;

    public static final int REASONABLE_WALKING_TIME_MINUTES = 15;

    public static final int MIN_WALKING_TIME_MINUTES = 1;


    public static final double DEFAULT_SEARCH_RADIUS_METERS = 800.0;

    public static final double MAX_SEARCH_RADIUS_METERS = 5000.0;

    public static final double MIN_SEARCH_RADIUS_METERS = 100.0;


    public static final double[] LAYERED_SEARCH_RADIUSES_KM = {0.3, 0.6, 1.0};

    public static final int[] MAX_STOPS_PER_LAYER = {4, 6, 8};


    public static final int MIN_URBAN_CORRECTION_MINUTES = 1;
    public static final int MAX_URBAN_CORRECTION_MINUTES = 3;

    public static final double TRAFFIC_MULTIPLIER_NO_TRAFFIC = 1.0;
    public static final double TRAFFIC_MULTIPLIER_LIGHT = 1.1;
    public static final double TRAFFIC_MULTIPLIER_MODERATE = 1.2;
    public static final double TRAFFIC_MULTIPLIER_HEAVY = 1.4;


    public static final int MAX_VEHICLE_POSITION_AGE_MINUTES = 10;

    public static final double MIN_MOTION_SPEED_KMH = 5.0;


    public static final int MIN_LINESTRING_POINTS = 2;

    public static final int MIN_POLYGON_POINTS = 4;

    public static final int MAX_GEOMETRY_POINTS = 10_000;


    public static final double METERS_PER_KILOMETER = 1000.0;

    public static final double METERS_PER_MILE = 1609.34;

    public static final double KILOMETERS_PER_MILE = 1.60934;


    public static String getDocumentation() {
        return """
            Geospatial Constants Reference:

            Earth Radius: %.0f meters
            Coordinate Precision: %d decimal places (~11cm)
            Preferred SRID: %d (WGS84)

            Walking Speed: %.1f km/h (%.2f m/min)
            Max Walking Time: %d minutes
            Default Search Radius: %.0f meters

            For more details, see:
            - Haversine formula: https://en.wikipedia.org/wiki/Haversine_formula
            - WGS84: https://en.wikipedia.org/wiki/World_Geodetic_System
            - PostGIS: https://postgis.net/documentation/
            """.formatted(
                EARTH_RADIUS_METERS,
                COORDINATE_DECIMAL_PLACES,
                SRID_WGS84,
                AVERAGE_WALKING_SPEED_KMH,
                AVERAGE_WALKING_SPEED_M_PER_MIN,
                MAX_WALKING_TIME_MINUTES,
                DEFAULT_SEARCH_RADIUS_METERS
            );
    }
}
