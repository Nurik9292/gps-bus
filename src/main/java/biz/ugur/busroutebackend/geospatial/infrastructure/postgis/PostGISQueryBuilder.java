package biz.ugur.busroutebackend.geospatial.infrastructure.postgis;

import static biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISConstants.*;

/**
 * Builder for PostGIS spatial query fragments.
 *
 * <p>This utility class generates standardized SQL fragments for PostGIS spatial operations,
 * ensuring consistency and reducing code duplication across repositories.</p>
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * String distanceColumn = PostGISQueryBuilder.geographyDistanceInKm(
 *     "latitude", "longitude",
 *     ":centerLat", ":centerLon"
 * );
 *
 * String sql = String.format("""
 *     SELECT *, %s as distance_km
 *     FROM bus_stops
 *     WHERE %s
 *     ORDER BY distance_km
 *     """, distanceColumn, withinRadiusCondition);
 * }</pre>
 *
 * @see PostGISConstants
 */
public final class PostGISQueryBuilder {

    private PostGISQueryBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // ==================== Point Creation ====================

    /**
     * Generate ST_Point expression for given longitude and latitude columns/placeholders.
     *
     * @param longitudeExpr SQL expression for longitude (column name or placeholder like ":lon")
     * @param latitudeExpr SQL expression for latitude (column name or placeholder like ":lat")
     * @return ST_Point SQL fragment
     *
     * @example
     * <pre>{@code
     * pointExpression("longitude", "latitude")
     * // Returns: "ST_Point(longitude, latitude)"
     *
     * pointExpression(":centerLon", ":centerLat")
     * // Returns: "ST_Point(:centerLon, :centerLat)"
     * }</pre>
     */
    public static String pointExpression(String longitudeExpr, String latitudeExpr) {
        return String.format("ST_Point(%s, %s)", longitudeExpr, latitudeExpr);
    }

    /**
     * Generate ST_SetSRID expression for WGS84 coordinate system.
     *
     * @param pointExpr ST_Point expression
     * @return ST_SetSRID SQL fragment with SRID 4326
     *
     * @example
     * <pre>{@code
     * setSRID_WGS84("ST_Point(lon, lat)")
     * // Returns: "ST_SetSRID(ST_Point(lon, lat), 4326)"
     * }</pre>
     */
    public static String setSRID_WGS84(String pointExpr) {
        return String.format("ST_SetSRID(%s, %d)", pointExpr, SRID_WGS84);
    }

    /**
     * Generate complete WGS84 point expression (ST_Point + ST_SetSRID).
     *
     * @param longitudeExpr SQL expression for longitude
     * @param latitudeExpr SQL expression for latitude
     * @return Complete point expression with SRID
     *
     * @example
     * <pre>{@code
     * wgs84Point("longitude", "latitude")
     * // Returns: "ST_SetSRID(ST_Point(longitude, latitude), 4326)"
     * }</pre>
     */
    public static String wgs84Point(String longitudeExpr, String latitudeExpr) {
        return setSRID_WGS84(pointExpression(longitudeExpr, latitudeExpr));
    }

    // ==================== Geography Type Conversion ====================

    /**
     * Convert geometry to geography type for accurate distance calculations.
     *
     * @param geometryExpr PostGIS geometry expression
     * @return Geography cast expression
     *
     * @example
     * <pre>{@code
     * toGeography("ST_SetSRID(ST_Point(lon, lat), 4326)")
     * // Returns: "ST_SetSRID(ST_Point(lon, lat), 4326)::geography"
     * }</pre>
     */
    public static String toGeography(String geometryExpr) {
        return geometryExpr + "::geography";
    }

    // ==================== Distance Calculations ====================

    /**
     * Generate ST_Distance expression using geography type (RECOMMENDED).
     *
     * <p>This method provides accurate distance calculations on the WGS84 spheroid,
     * suitable for distances up to thousands of kilometers with < 0.5% error.</p>
     *
     * @param lon1Expr Longitude expression for point 1
     * @param lat1Expr Latitude expression for point 1
     * @param lon2Expr Longitude expression for point 2
     * @param lat2Expr Latitude expression for point 2
     * @return ST_Distance expression returning distance in meters
     *
     * @example
     * <pre>{@code
     * geographyDistanceInMeters("longitude", "latitude", ":centerLon", ":centerLat")
     * // Returns:
     * // ST_Distance(
     * //     ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
     * //     ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography
     * // )
     * }</pre>
     */
    public static String geographyDistanceInMeters(String lon1Expr, String lat1Expr,
                                                    String lon2Expr, String lat2Expr) {
        return String.format(
                "ST_Distance(\n" +
                "    %s,\n" +
                "    %s\n" +
                ")",
                toGeography(wgs84Point(lon1Expr, lat1Expr)),
                toGeography(wgs84Point(lon2Expr, lat2Expr))
        );
    }

    /**
     * Generate ST_Distance expression returning distance in kilometers.
     *
     * @param lon1Expr Longitude expression for point 1
     * @param lat1Expr Latitude expression for point 1
     * @param lon2Expr Longitude expression for point 2
     * @param lat2Expr Latitude expression for point 2
     * @return ST_Distance expression returning distance in kilometers
     *
     * @example
     * <pre>{@code
     * geographyDistanceInKm("longitude", "latitude", ":centerLon", ":centerLat")
     * // Returns: ST_Distance(...) / 1000.0
     * }</pre>
     */
    public static String geographyDistanceInKm(String lon1Expr, String lat1Expr,
                                                String lon2Expr, String lat2Expr) {
        return String.format(
                "%s / %.1f",
                geographyDistanceInMeters(lon1Expr, lat1Expr, lon2Expr, lat2Expr),
                METERS_PER_KILOMETER
        );
    }

    // ==================== Spatial Predicates ====================

    /**
     * Generate ST_DWithin condition for geography type (RECOMMENDED).
     *
     * <p>ST_DWithin is more efficient than ST_Distance for "within radius" checks
     * because it can use spatial indexes and short-circuit when points are clearly too far.</p>
     *
     * @param lon1Expr Longitude expression for point 1
     * @param lat1Expr Latitude expression for point 1
     * @param lon2Expr Longitude expression for point 2
     * @param lat2Expr Latitude expression for point 2
     * @param radiusMetersExpr Radius expression in meters (can be placeholder like ":radius")
     * @return ST_DWithin condition
     *
     * @example
     * <pre>{@code
     * withinRadiusCondition("longitude", "latitude", ":centerLon", ":centerLat", ":radiusMeters")
     * // Returns:
     * // ST_DWithin(
     * //     ST_SetSRID(ST_Point(longitude, latitude), 4326)::geography,
     * //     ST_SetSRID(ST_Point(:centerLon, :centerLat), 4326)::geography,
     * //     :radiusMeters
     * // )
     * }</pre>
     */
    public static String withinRadiusCondition(String lon1Expr, String lat1Expr,
                                               String lon2Expr, String lat2Expr,
                                               String radiusMetersExpr) {
        return String.format(
                "ST_DWithin(\n" +
                "    %s,\n" +
                "    %s,\n" +
                "    %s\n" +
                ")",
                toGeography(wgs84Point(lon1Expr, lat1Expr)),
                toGeography(wgs84Point(lon2Expr, lat2Expr)),
                radiusMetersExpr
        );
    }

    /**
     * Alternative version of withinRadiusCondition using ST_Distance for comparison.
     *
     * <p><strong>NOTE:</strong> Less efficient than ST_DWithin. Use {@link #withinRadiusCondition}
     * when possible.</p>
     *
     * @param lon1Expr Longitude expression for point 1
     * @param lat1Expr Latitude expression for point 1
     * @param lon2Expr Longitude expression for point 2
     * @param lat2Expr Latitude expression for point 2
     * @param radiusMetersExpr Radius expression in meters
     * @return Distance comparison condition
     */
    public static String distanceLessThanCondition(String lon1Expr, String lat1Expr,
                                                   String lon2Expr, String lat2Expr,
                                                   String radiusMetersExpr) {
        return String.format(
                "%s <= %s",
                geographyDistanceInMeters(lon1Expr, lat1Expr, lon2Expr, lat2Expr),
                radiusMetersExpr
        );
    }

    // ==================== Complete Query Fragments ====================

    /**
     * Generate complete "nearby points" query fragment with distance calculation and filtering.
     *
     * <p>This method generates both the distance column and WHERE condition for finding
     * points within a specified radius.</p>
     *
     * @param tableLonColumn Table column name for longitude (e.g., "longitude" or "t.longitude")
     * @param tableLatColumn Table column name for latitude (e.g., "latitude" or "t.latitude")
     * @param centerLonParam Parameter name for center longitude (e.g., ":centerLon")
     * @param centerLatParam Parameter name for center latitude (e.g., ":centerLat")
     * @param radiusMetersParam Parameter name for radius in meters (e.g., ":radiusMeters")
     * @param distanceColumnAlias Alias for distance column (e.g., "distance_km" or "distance_meters")
     * @param distanceUnit Unit for distance column ("m" for meters, "km" for kilometers)
     * @return Complete query fragment with SELECT and WHERE clauses
     *
     * @example
     * <pre>{@code
     * QueryFragment fragment = nearbyPointsQuery(
     *     "longitude", "latitude",
     *     ":centerLon", ":centerLat",
     *     ":radiusMeters",
     *     "distance_km", "km"
     * );
     *
     * String sql = String.format("""
     *     SELECT *, %s
     *     FROM bus_stops
     *     WHERE is_active = true AND %s
     *     ORDER BY %s
     *     """,
     *     fragment.distanceColumn(),
     *     fragment.withinRadiusCondition(),
     *     fragment.orderByClause()
     * );
     * }</pre>
     */
    public static QueryFragment nearbyPointsQuery(String tableLonColumn, String tableLatColumn,
                                                   String centerLonParam, String centerLatParam,
                                                   String radiusMetersParam,
                                                   String distanceColumnAlias,
                                                   String distanceUnit) {
        String distanceExpr;
        if (UNIT_KILOMETERS.equals(distanceUnit)) {
            distanceExpr = geographyDistanceInKm(tableLonColumn, tableLatColumn,
                    centerLonParam, centerLatParam);
        } else {
            distanceExpr = geographyDistanceInMeters(tableLonColumn, tableLatColumn,
                    centerLonParam, centerLatParam);
        }

        String distanceColumn = distanceExpr + " as " + distanceColumnAlias;
        String withinCondition = withinRadiusCondition(tableLonColumn, tableLatColumn,
                centerLonParam, centerLatParam, radiusMetersParam);
        String orderBy = distanceColumnAlias;

        return new QueryFragment(distanceColumn, withinCondition, orderBy);
    }

    /**
     * Immutable record holding generated query fragments.
     *
     * @param distanceColumn Distance calculation expression with alias (for SELECT clause)
     * @param withinRadiusCondition ST_DWithin condition (for WHERE clause)
     * @param orderByClause Column name for ordering results by distance
     */
    public record QueryFragment(
            String distanceColumn,
            String withinRadiusCondition,
            String orderByClause
    ) {}
}
