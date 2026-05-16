ALTER TABLE ad_placements
    DROP CONSTRAINT ad_placements_status_chk;

ALTER TABLE ad_placements
    ADD CONSTRAINT ad_placements_status_chk
        CHECK (status IN ('DRAFT', 'PENDING_PAYMENT', 'SCHEDULED', 'ACTIVE', 'PAUSED', 'EXPIRED', 'CANCELLED'));
