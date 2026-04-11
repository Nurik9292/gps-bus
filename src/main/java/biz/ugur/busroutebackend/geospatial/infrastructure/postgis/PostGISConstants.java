package biz.ugur.busroutebackend.geospatial.infrastructure.postgis;


public final class PostGISConstants {

    private PostGISConstants() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    public static final int SRID_WGS84 = 4326;

    public static final String METHOD_GEOGRAPHY = "geography";

    public static final String UNIT_METERS = "m";

    public static final String UNIT_KILOMETERS = "km";

    public static final double METERS_PER_KILOMETER = 1000.0;

    public static final int DEFAULT_SPATIAL_QUERY_LIMIT = 50;

    public static final double MAX_SEARCH_RADIUS_KM = 50.0;

    public static final double MIN_SEARCH_RADIUS_METERS = 1.0;
}
