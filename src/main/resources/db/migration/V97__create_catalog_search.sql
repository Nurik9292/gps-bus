CREATE OR REPLACE FUNCTION search_norm(t text) RETURNS text
IMMUTABLE PARALLEL SAFE LANGUAGE sql AS $$
  SELECT trim(regexp_replace(regexp_replace(
    translate(lower(coalesce(t, '')),
      'çäžşňüöýÇÄŽŞŇÜÖÝёЁıİабвгдежзийклмнопрстуфхцчшщыэюяАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЫЭЮЯъьЪЬ',
      'cajsnuoycajsnuoyeeiiabvgdejziiklmnoprstufhccssyeuaabvgdejziiklmnoprstufhccssyeua'),
    '[^a-z0-9 ]+', ' ', 'g'), ' +', ' ', 'g'))
$$;

CREATE TABLE search_alias (
    id           BIGSERIAL PRIMARY KEY,
    object_kind  VARCHAR(10)  NOT NULL,
    object_id    VARCHAR(36)  NOT NULL,
    alias_raw    VARCHAR(300) NOT NULL,
    alias_norm   VARCHAR(300) NOT NULL,
    weight       NUMERIC(3,1) NOT NULL DEFAULT 1.0,
    source       VARCHAR(20)  NOT NULL DEFAULT 'CURATED',
    created_by   VARCHAR(36),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT search_alias_kind_chk   CHECK (object_kind IN ('STOP','ROUTE')),
    CONSTRAINT search_alias_weight_chk CHECK (weight >= 0.1 AND weight <= 3.0),
    CONSTRAINT search_alias_uniq UNIQUE (object_kind, object_id, alias_raw)
);

CREATE INDEX idx_search_alias_object    ON search_alias (object_kind, object_id);
CREATE INDEX idx_search_alias_norm_trgm ON search_alias USING gin (alias_norm gin_trgm_ops);

CREATE TABLE search_index (
    object_kind  VARCHAR(10)  NOT NULL,
    object_id    VARCHAR(36)  NOT NULL,
    term_norm    VARCHAR(300) NOT NULL,
    title        VARCHAR(220) NOT NULL,
    subtitle     VARCHAR(200),
    weight       NUMERIC(3,1) NOT NULL,
    source       VARCHAR(20)  NOT NULL,
    PRIMARY KEY (object_kind, object_id, term_norm)
);

CREATE INDEX idx_search_index_trgm ON search_index USING gin (term_norm gin_trgm_ops);

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
               s.title, s.subtitle, s.weight, s.source, s.prio
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
