package biz.ugur.busroutebackend.place.infrastructure.query;

import java.util.HashMap;
import java.util.Map;

public final class PlaceSearchQueryBuilder {

    private PlaceSearchQueryBuilder() {}

    public record SearchQuery(String sql, Map<String, Object> params) {}

    public static SearchQuery buildSearchQuery(String query, String cityId, String category, int limit, double threshold) {
        Map<String, Object> params = new HashMap<>();
        params.put("limit", limit);

        if (query.length() <= 3) {
            return buildShortSearchQuery(query, cityId, category, params);
        }
        return buildFuzzySearchQuery(query, cityId, category, threshold, params);
    }

    private static SearchQuery buildShortSearchQuery(String query, String cityId, String category, Map<String, Object> params) {
        String pattern = "%" + query + "%";
        params.put("pattern", pattern);

        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.name_en, p.name_tm, p.description, p.address,
                       p.category, p.latitude, p.longitude, p.city_id,
                       COALESCE(array_agg(pa.alias) FILTER (WHERE pa.alias IS NOT NULL), '{}') AS aliases,
                       1.0 AS score
                FROM places p
                LEFT JOIN place_aliases pa ON p.id = pa.place_id
                WHERE p.is_active = true
                  AND (p.name ILIKE :pattern OR p.name_en ILIKE :pattern OR p.name_tm ILIKE :pattern
                       OR pa.alias ILIKE :pattern)
                """);

        appendFilters(sql, cityId, category, params);

        sql.append(" GROUP BY p.id ORDER BY p.name ASC LIMIT :limit");

        return new SearchQuery(sql.toString(), params);
    }

    private static SearchQuery buildFuzzySearchQuery(String query, String cityId, String category, double threshold, Map<String, Object> params) {
        params.put("query", query);
        params.put("threshold", threshold);
        params.put("pattern", "%" + query + "%");

        StringBuilder sql = new StringBuilder("""
                SELECT p.id, p.name, p.name_en, p.name_tm, p.description, p.address,
                       p.category, p.latitude, p.longitude, p.city_id,
                       COALESCE(array_agg(pa.alias) FILTER (WHERE pa.alias IS NOT NULL), '{}') AS aliases,
                       GREATEST(
                           similarity(p.name, :query),
                           COALESCE(similarity(p.name_en, :query), 0),
                           COALESCE(similarity(p.name_tm, :query), 0),
                           COALESCE(MAX(similarity(pa.alias, :query)), 0),
                           CASE WHEN p.name ILIKE :pattern THEN 0.5 ELSE 0 END,
                           CASE WHEN p.name_en ILIKE :pattern THEN 0.4 ELSE 0 END,
                           CASE WHEN p.name_tm ILIKE :pattern THEN 0.4 ELSE 0 END
                       ) AS score
                FROM places p
                LEFT JOIN place_aliases pa ON p.id = pa.place_id
                WHERE p.is_active = true
                  AND (
                       p.name ILIKE :pattern
                       OR p.name_en ILIKE :pattern
                       OR p.name_tm ILIKE :pattern
                       OR EXISTS (SELECT 1 FROM place_aliases pa2 WHERE pa2.place_id = p.id AND pa2.alias ILIKE :pattern)
                       OR similarity(p.name, :query) > :threshold
                       OR similarity(p.name_en, :query) > :threshold
                       OR similarity(p.name_tm, :query) > :threshold
                       OR EXISTS (SELECT 1 FROM place_aliases pa2 WHERE pa2.place_id = p.id AND similarity(pa2.alias, :query) > :threshold)
                  )
                """);

        appendFilters(sql, cityId, category, params);

        sql.append(" GROUP BY p.id ORDER BY score DESC LIMIT :limit");

        return new SearchQuery(sql.toString(), params);
    }

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

    public static SearchQuery buildAutocompleteQuery(String query, String cityId, int limit) {
        Map<String, Object> params = new HashMap<>();
        String pattern = query + "%";
        params.put("pattern", pattern);
        params.put("limit", limit);

        StringBuilder sql = new StringBuilder("""
                SELECT DISTINCT p.id, p.name, p.category, p.address
                FROM places p
                LEFT JOIN place_aliases pa ON p.id = pa.place_id
                WHERE p.is_active = true
                  AND (p.name ILIKE :pattern OR p.name_en ILIKE :pattern OR p.name_tm ILIKE :pattern
                       OR pa.alias ILIKE :pattern)
                """);

        if (cityId != null && !cityId.isBlank()) {
            sql.append(" AND p.city_id = :cityId");
            params.put("cityId", cityId);
        }

        sql.append(" ORDER BY p.name ASC LIMIT :limit");

        return new SearchQuery(sql.toString(), params);
    }

    private static void appendFilters(StringBuilder sql, String cityId, String category, Map<String, Object> params) {
        if (cityId != null && !cityId.isBlank()) {
            sql.append(" AND p.city_id = :cityId");
            params.put("cityId", cityId);
        }
        if (category != null && !category.isBlank()) {
            sql.append(" AND p.category = :category");
            params.put("category", category);
        }
    }
}
