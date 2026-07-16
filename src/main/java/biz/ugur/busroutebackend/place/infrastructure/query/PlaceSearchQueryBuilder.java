package biz.ugur.busroutebackend.place.infrastructure.query;

import java.util.HashMap;
import java.util.Map;

public final class PlaceSearchQueryBuilder {

    private PlaceSearchQueryBuilder() {}

    public record SearchQuery(String sql, Map<String, Object> params) {}

    public static SearchQuery buildNearbyQuery(double lat, double lng, int radiusMeters, String category, int limit) {
        Map<String, Object> params = new HashMap<>();
        params.put("lat", lat);
        params.put("lng", lng);
        params.put("radius", radiusMeters);
        params.put("limit", limit);

        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.name_en, p.name_tm, p.description, p.address,
                       p.category, p.latitude, p.longitude, p.city_id,
                       COALESCE(array_agg(pa.alias) FILTER (WHERE pa.alias IS NOT NULL), '{}') AS aliases,
                       ST_Distance(
                           ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography,
                           ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography
                       ) AS distance
                FROM places p
                LEFT JOIN place_aliases pa ON p.id = pa.place_id
                WHERE p.is_active = true
                  AND ST_DWithin(
                      ST_SetSRID(ST_MakePoint(p.longitude, p.latitude), 4326)::geography,
                      ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography,
                      :radius
                  )
                """);

        if (category != null && !category.isBlank()) {
            sql.append(" AND p.category = :category");
            params.put("category", category);
        }

        sql.append(" GROUP BY p.id ORDER BY distance ASC LIMIT :limit");

        return new SearchQuery(sql.toString(), params);
    }
}
