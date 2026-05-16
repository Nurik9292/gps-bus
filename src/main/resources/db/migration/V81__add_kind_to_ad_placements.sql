ALTER TABLE ad_placements
    ADD COLUMN IF NOT EXISTS kind VARCHAR(20) NOT NULL DEFAULT 'COMMERCIAL'
        CHECK (kind IN ('COMMERCIAL', 'EDITORIAL'));

CREATE INDEX IF NOT EXISTS idx_ad_placements_kind ON ad_placements(kind);

COMMENT ON COLUMN ad_placements.kind IS
    'COMMERCIAL = paid placement bought by a business, EDITORIAL = platform-managed (migrated from legacy banners). Default COMMERCIAL for existing rows.';
