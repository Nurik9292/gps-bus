CREATE TABLE IF NOT EXISTS ad_placement_targets (
    id              VARCHAR(36) PRIMARY KEY,
    placement_id    VARCHAR(36) NOT NULL,
    target_type     VARCHAR(40) NOT NULL,
    target_id       VARCHAR(36),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT ad_placement_targets_placement_fk
        FOREIGN KEY (placement_id) REFERENCES ad_placements(id) ON DELETE CASCADE,

    CONSTRAINT ad_placement_targets_type_chk
        CHECK (target_type IN (
            'HOME', 'POPUP', 'ROUTES_LIST', 'STOPS_LIST', 'PLACES_LIST',
            'ROUTE', 'STOP', 'PLACE')),

    CONSTRAINT ad_placement_targets_id_shape_chk
        CHECK (
            (target_type IN ('ROUTE', 'STOP', 'PLACE') AND target_id IS NOT NULL)
            OR
            (target_type IN ('HOME', 'POPUP', 'ROUTES_LIST', 'STOPS_LIST', 'PLACES_LIST') AND target_id IS NULL)
        )
);

CREATE INDEX IF NOT EXISTS idx_apt_placement
    ON ad_placement_targets(placement_id);

CREATE INDEX IF NOT EXISTS idx_apt_type_id
    ON ad_placement_targets(target_type, target_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_apt_specific
    ON ad_placement_targets(placement_id, target_type, target_id)
    WHERE target_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_apt_general
    ON ad_placement_targets(placement_id, target_type)
    WHERE target_id IS NULL;

COMMENT ON TABLE  ad_placement_targets         IS 'Где конкретно показывается ad_placement: глобальные зоны (HOME, POPUP, *_LIST) или конкретный объект (ROUTE/STOP/PLACE).';
COMMENT ON COLUMN ad_placement_targets.target_id IS 'For ROUTE/STOP/PLACE — FK-like reference to bus_routes/bus_stops/places.id. No DB FK on purpose: existence is validated in application layer to avoid cascading complications.';
