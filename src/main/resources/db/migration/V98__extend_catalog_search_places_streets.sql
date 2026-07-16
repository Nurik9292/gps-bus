CREATE OR REPLACE FUNCTION catalog_search_rebuild(
    p_kind text DEFAULT NULL,
    p_id   text DEFAULT NULL
) RETURNS TABLE(inserted bigint, orphan_aliases bigint)
LANGUAGE plpgsql AS $$
DECLARE
    v_inserted bigint;
    v_orphans  bigint;
BEGIN
    IF (p_kind IS NULL) <> (p_id IS NULL) THEN
        RAISE EXCEPTION 'catalog_search_rebuild: p_kind and p_id must both be NULL or both set';
    END IF;
    PERFORM pg_advisory_xact_lock(hashtext('catalog_search_rebuild'));
    IF p_kind IS NULL THEN
        DELETE FROM search_index;
    ELSE
        PERFORM pg_advisory_xact_lock(hashtext(p_kind || ':' || p_id));
        DELETE FROM search_index WHERE object_kind = p_kind AND object_id = p_id;
    END IF;

    WITH source_rows AS (
        SELECT s.kind, s.id, search_norm(s.term) AS term_norm,
               left(s.title, 220) AS title, left(s.subtitle, 200) AS subtitle,
               s.weight, s.source, s.prio
        FROM (
            SELECT 'STOP' AS kind, bs.id, t.term,
                   bs.stop_name AS title, NULL::text AS subtitle,
                   1.0::numeric(3,1) AS weight, 'NAME' AS source, 0 AS prio
            FROM bus_stops bs
            CROSS JOIN LATERAL (VALUES (bs.stop_name), (bs.name_tm), (bs.name_en)) t(term)
            WHERE bs.is_active AND t.term IS NOT NULL

            UNION ALL

            SELECT 'ROUTE', br.id, br.route_number,
                   br.route_number, br.route_name,
                   2.0, 'NAME', 0
            FROM bus_routes br
            WHERE br.is_active

            UNION ALL

            SELECT 'ROUTE', br.id, t.term,
                   br.route_number, br.route_name,
                   1.0, 'NAME', 0
            FROM bus_routes br
            CROSS JOIN LATERAL (VALUES (br.route_name), (br.name_tm), (br.name_en)) t(term)
            WHERE br.is_active AND t.term IS NOT NULL

            UNION ALL

            SELECT sa.object_kind, sa.object_id, sa.alias_raw,
                   bs.stop_name, NULL,
                   sa.weight, sa.source,
                   CASE sa.source WHEN 'CURATED' THEN 1 WHEN 'COLLOQUIAL' THEN 2 ELSE 3 END
            FROM search_alias sa
            JOIN bus_stops bs ON sa.object_kind = 'STOP' AND bs.id = sa.object_id AND bs.is_active

            UNION ALL

            SELECT sa.object_kind, sa.object_id, sa.alias_raw,
                   br.route_number, br.route_name,
                   sa.weight, sa.source,
                   CASE sa.source WHEN 'CURATED' THEN 1 WHEN 'COLLOQUIAL' THEN 2 ELSE 3 END
            FROM search_alias sa
            JOIN bus_routes br ON sa.object_kind = 'ROUTE' AND br.id = sa.object_id AND br.is_active

            UNION ALL

            SELECT 'PLACE', p.id, t.term,
                   p.name, NULLIF(concat_ws(' · ', p.category, p.address), ''),
                   1.0, 'NAME', 0
            FROM places p
            CROSS JOIN LATERAL (VALUES (p.name), (p.name_en), (p.name_tm)) t(term)
            WHERE p.is_active AND t.term IS NOT NULL

            UNION ALL

            SELECT 'PLACE', pa.place_id, pa.alias,
                   p.name, NULLIF(concat_ws(' · ', p.category, p.address), ''),
                   1.0, 'CURATED', 1
            FROM place_aliases pa
            JOIN places p ON p.id = pa.place_id AND p.is_active

            UNION ALL

            SELECT 'STREET', st.id, t.term,
                   st.name, c.name,
                   1.0, 'NAME', 0
            FROM streets st
            LEFT JOIN cities c ON c.id = st.city_id
            CROSS JOIN LATERAL (VALUES (st.name), (st.name_en), (st.name_tm)) t(term)
            WHERE st.is_active AND t.term IS NOT NULL

            UNION ALL

            SELECT 'STREET', sta.street_id, sta.alias,
                   st.name, c.name,
                   1.0, 'CURATED', 1
            FROM street_aliases sta
            JOIN streets st ON st.id = sta.street_id AND st.is_active
            LEFT JOIN cities c ON c.id = st.city_id
        ) s
        WHERE (p_kind IS NULL OR (s.kind = p_kind AND s.id = p_id))
    )
    INSERT INTO search_index (object_kind, object_id, term_norm, title, subtitle, weight, source)
    SELECT DISTINCT ON (kind, id, term_norm) kind, id, term_norm, title, subtitle, weight, source
    FROM source_rows
    WHERE term_norm <> ''
    ORDER BY kind, id, term_norm, prio ASC, weight DESC
    ON CONFLICT (object_kind, object_id, term_norm) DO NOTHING;

    GET DIAGNOSTICS v_inserted = ROW_COUNT;

    SELECT count(*) INTO v_orphans
    FROM search_alias sa
    WHERE (p_kind IS NULL OR (sa.object_kind = p_kind AND sa.object_id = p_id))
      AND ((sa.object_kind = 'STOP' AND NOT EXISTS (
                SELECT 1 FROM bus_stops b WHERE b.id = sa.object_id AND b.is_active))
        OR (sa.object_kind = 'ROUTE' AND NOT EXISTS (
                SELECT 1 FROM bus_routes r WHERE r.id = sa.object_id AND r.is_active)));

    RETURN QUERY SELECT v_inserted, v_orphans;
END;
$$;

DROP INDEX IF EXISTS idx_streets_name_trgm;
DROP INDEX IF EXISTS idx_street_aliases_alias_trgm;
