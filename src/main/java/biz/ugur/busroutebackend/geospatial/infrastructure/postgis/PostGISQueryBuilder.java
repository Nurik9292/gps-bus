package biz.ugur.busroutebackend.geospatial.infrastructure.postgis;

import static biz.ugur.busroutebackend.geospatial.infrastructure.postgis.PostGISConstants.*;

public final class PostGISQueryBuilder {

    private PostGISQueryBuilder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }


    public static String pointExpression(String longitudeExpr, String latitudeExpr) {
        return String.format("ST_Point(%s, %s)", longitudeExpr, latitudeExpr);
    }


    public static String setSRID_WGS84(String pointExpr) {
        return String.format("ST_SetSRID(%s, %d)", pointExpr, SRID_WGS84);
    }


    public static String wgs84Point(String longitudeExpr, String latitudeExpr) {
        return setSRID_WGS84(pointExpression(longitudeExpr, latitudeExpr));
    }

    public static String toGeography(String geometryExpr) {
        return geometryExpr + "::geography";
    }


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


    public static String geographyDistanceInKm(String lon1Expr, String lat1Expr,
                                                String lon2Expr, String lat2Expr) {
        return String.format(
                "%s / %.1f",
                geographyDistanceInMeters(lon1Expr, lat1Expr, lon2Expr, lat2Expr),
                METERS_PER_KILOMETER
        );
    }

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


    public static String distanceLessThanCondition(String lon1Expr, String lat1Expr,
                                                   String lon2Expr, String lat2Expr,
                                                   String radiusMetersExpr) {
        return String.format(
                "%s <= %s",
                geographyDistanceInMeters(lon1Expr, lat1Expr, lon2Expr, lat2Expr),
                radiusMetersExpr
        );
    }


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

        return new QueryFragment(distanceColumn, withinCondition, distanceColumnAlias);
    }

    public record QueryFragment(
            String distanceColumn,
            String withinRadiusCondition,
            String orderByClause
    ) {}


    public static String pointInPolygonCondition(String polygonExpr, String lonExpr, String latExpr) {
        return String.format(
                "ST_Contains(%s::geometry, %s::geometry)",
                polygonExpr,
                wgs84Point(lonExpr, latExpr)
        );
    }


    public static String distanceToPolygonInMeters(String polygonExpr, String lonExpr, String latExpr) {
        return String.format(
                "ST_Distance(%s, %s)",
                polygonExpr,
                toGeography(wgs84Point(lonExpr, latExpr))
        );
    }


    public static String pointOutsidePolygonWithBuffer(String polygonExpr, String lonExpr, String latExpr, String bufferMetersExpr) {
        return String.format(
                "NOT ST_DWithin(%s, %s, %s)",
                polygonExpr,
                toGeography(wgs84Point(lonExpr, latExpr)),
                bufferMetersExpr
        );
    }


    public static String pointInGarageCondition(
            String boundaryColumn,
            String centerLonColumn,
            String centerLatColumn,
            String radiusColumn,
            String pointLonExpr,
            String pointLatExpr) {
        return String.format(
                """
                CASE
                    WHEN %s IS NOT NULL THEN %s
                    ELSE %s
                END""",
                boundaryColumn,
                pointInPolygonCondition(boundaryColumn, pointLonExpr, pointLatExpr),
                withinRadiusCondition(centerLonColumn, centerLatColumn, pointLonExpr, pointLatExpr, radiusColumn)
        );
    }
}
