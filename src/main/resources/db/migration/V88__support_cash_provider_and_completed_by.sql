ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_provider_chk;
ALTER TABLE payments ADD CONSTRAINT payments_provider_chk
    CHECK (provider IN ('RYSGAL', 'SENAGAT', 'BKB', 'HALK', 'CASH'));

ALTER TABLE payments ADD COLUMN IF NOT EXISTS completed_by VARCHAR(100);
