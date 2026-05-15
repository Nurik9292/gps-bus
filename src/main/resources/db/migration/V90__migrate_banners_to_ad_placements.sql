INSERT INTO ad_placements (
    id, business_id, tariff_id, placement_type, kind, status,
    title, content, image_url, target_url, cta_text, content_type,
    starts_at, ends_at, display_order,
    version, created_at, updated_at
)
SELECT
    b.id,
    NULL,
    NULL,
    'BANNER',
    'EDITORIAL',
    CASE
        WHEN b.is_active = false                                  THEN 'CANCELLED'
        WHEN b.end_date IS NOT NULL AND b.end_date <= NOW()       THEN 'EXPIRED'
        WHEN b.start_date IS NOT NULL AND b.start_date > NOW()    THEN 'SCHEDULED'
        ELSE 'ACTIVE'
    END,
    b.title,
    b.content,
    b.image_url,
    b.target_url,
    NULL,
    CASE WHEN b.content IS NOT NULL AND b.content <> '' THEN 'CONTENT' ELSE 'LINK' END,
    b.start_date,
    b.end_date,
    COALESCE(b.display_order, 0),
    0,
    b.created_at,
    b.updated_at
FROM banners b
WHERE b.type IN ('main', 'stops', 'routes', 'places', 'popup')
  AND (
      (b.content IS NOT NULL AND b.content <> '')
      OR b.target_url IS NOT NULL
  );

INSERT INTO ad_placement_targets (id, placement_id, target_type, target_id, created_at)
SELECT
    gen_random_uuid()::text,
    b.id,
    CASE b.type
        WHEN 'main'   THEN 'HOME'
        WHEN 'stops'  THEN 'STOPS_LIST'
        WHEN 'routes' THEN 'ROUTES_LIST'
        WHEN 'places' THEN 'PLACES_LIST'
        WHEN 'popup'  THEN 'POPUP'
    END,
    NULL,
    NOW()
FROM banners b
WHERE b.type IN ('main', 'stops', 'routes', 'places', 'popup')
  AND (
      (b.content IS NOT NULL AND b.content <> '')
      OR b.target_url IS NOT NULL
  );

COMMENT ON TABLE banners IS
    'DEPRECATED — migrated to ad_placements (kind=EDITORIAL) in V90 (Sprint N+4). Kept for rollback until N+5.';
