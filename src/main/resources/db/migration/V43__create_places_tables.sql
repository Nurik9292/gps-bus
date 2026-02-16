
CREATE EXTENSION IF NOT EXISTS pg_trgm;


CREATE TABLE IF NOT EXISTS places (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(300) NOT NULL,
    name_en     VARCHAR(300),
    name_tm     VARCHAR(300),
    description TEXT,
    address     VARCHAR(500),
    category    VARCHAR(50) NOT NULL,
    city_id     VARCHAR(36) NOT NULL REFERENCES cities(id),
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT chk_places_category CHECK (
        category IN ('MALL', 'BAZAAR', 'RESTAURANT', 'MOSQUE', 'PARK', 'LANDMARK',
                      'HOTEL', 'HOSPITAL', 'EDUCATION', 'GOVERNMENT', 'BANK',
                      'PHARMACY', 'GAS_STATION', 'OTHER')
    ),
    CONSTRAINT chk_places_latitude CHECK (latitude BETWEEN 35.0 AND 43.0),
    CONSTRAINT chk_places_longitude CHECK (longitude BETWEEN 52.0 AND 67.0)
);

CREATE INDEX IF NOT EXISTS idx_places_name_trgm ON places USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_places_name_en_trgm ON places USING GIN (name_en gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_places_name_tm_trgm ON places USING GIN (name_tm gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_places_location ON places USING GIST (
    ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography
);

CREATE INDEX IF NOT EXISTS idx_places_city_id ON places (city_id);
CREATE INDEX IF NOT EXISTS idx_places_category ON places (category);
CREATE INDEX IF NOT EXISTS idx_places_is_active ON places (is_active);


CREATE TABLE IF NOT EXISTS place_aliases (
    id          VARCHAR(36) PRIMARY KEY,
    place_id    VARCHAR(36) NOT NULL REFERENCES places(id) ON DELETE CASCADE,
    alias       VARCHAR(300) NOT NULL,
    language    VARCHAR(5) DEFAULT 'ru',
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_place_aliases_alias_trgm ON place_aliases USING GIN (alias gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_place_aliases_place_id ON place_aliases (place_id);


CREATE TABLE IF NOT EXISTS streets (
    id          VARCHAR(36) PRIMARY KEY,
    name        VARCHAR(300) NOT NULL,
    name_en     VARCHAR(300),
    name_tm     VARCHAR(300),
    city_id     VARCHAR(36) NOT NULL REFERENCES cities(id),
    is_active   BOOLEAN NOT NULL DEFAULT true,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_streets_name_trgm ON streets USING GIN (name gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_streets_city_id ON streets (city_id);


CREATE TABLE IF NOT EXISTS street_aliases (
    id          VARCHAR(36) PRIMARY KEY,
    street_id   VARCHAR(36) NOT NULL REFERENCES streets(id) ON DELETE CASCADE,
    alias       VARCHAR(300) NOT NULL,
    language    VARCHAR(5) DEFAULT 'ru',
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    version     BIGINT NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_street_aliases_alias_trgm ON street_aliases USING GIN (alias gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_street_aliases_street_id ON street_aliases (street_id);
