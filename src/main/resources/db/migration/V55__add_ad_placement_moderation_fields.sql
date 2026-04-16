ALTER TABLE ad_placements
    ADD COLUMN IF NOT EXISTS rejection_reason       TEXT,
    ADD COLUMN IF NOT EXISTS approved_at            TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS approved_by_admin_id   VARCHAR(36),
    ADD COLUMN IF NOT EXISTS rejected_at            TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS rejected_by_admin_id   VARCHAR(36);

CREATE INDEX IF NOT EXISTS idx_ad_placements_pending_moderation
    ON ad_placements (created_at DESC) WHERE status = 'DRAFT';

COMMENT ON COLUMN ad_placements.rejection_reason     IS 'Reason given by the moderator when rejecting the creative.';
COMMENT ON COLUMN ad_placements.approved_at          IS 'Timestamp of moderator approval (DRAFT -> PENDING_PAYMENT).';
COMMENT ON COLUMN ad_placements.approved_by_admin_id IS 'FK to admins.id of the moderator who approved the placement.';
COMMENT ON COLUMN ad_placements.rejected_at          IS 'Timestamp of moderator rejection (DRAFT -> CANCELLED with reason).';
COMMENT ON COLUMN ad_placements.rejected_by_admin_id IS 'FK to admins.id of the moderator who rejected the placement.';
