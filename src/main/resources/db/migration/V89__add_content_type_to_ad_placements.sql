ALTER TABLE ad_placements
    ADD COLUMN content_type VARCHAR(16) NOT NULL DEFAULT 'LINK';

ALTER TABLE ad_placements
    ADD CONSTRAINT ad_placements_content_type_chk
        CHECK (content_type IN ('CONTENT', 'LINK'));

ALTER TABLE ad_placements
    ADD CONSTRAINT ad_placements_content_consistency_chk
        CHECK (
            (content_type = 'CONTENT' AND content IS NOT NULL)
         OR (content_type = 'LINK'    AND target_url IS NOT NULL)
        );

ALTER TABLE ad_placements
    ALTER COLUMN business_id DROP NOT NULL,
    ALTER COLUMN tariff_id   DROP NOT NULL;

CREATE INDEX IF NOT EXISTS ix_ad_placements_kind_status_active
    ON ad_placements(kind, status)
    WHERE status = 'ACTIVE';
