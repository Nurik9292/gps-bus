ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_subject_type_chk;
ALTER TABLE payments ADD CONSTRAINT payments_subject_type_chk
    CHECK (subject_type IN ('AD_PLACEMENT', 'CLIENT_SUBSCRIPTION'));

CREATE TABLE IF NOT EXISTS client_subscriptions (
    id                  VARCHAR(36)              NOT NULL PRIMARY KEY,
    client_id           VARCHAR(36)              NOT NULL,
    period              VARCHAR(16)              NOT NULL,
    status              VARCHAR(20)              NOT NULL DEFAULT 'PENDING',
    payment_id          VARCHAR(36),
    amount_minor        BIGINT                   NOT NULL,
    currency            VARCHAR(3)               NOT NULL DEFAULT 'TMT',
    started_at          TIMESTAMP WITH TIME ZONE,
    expires_at          TIMESTAMP WITH TIME ZONE,
    cancelled_at        TIMESTAMP WITH TIME ZONE,
    cancellation_reason VARCHAR(255),
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT client_subscriptions_period_chk
        CHECK (period IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT client_subscriptions_status_chk
        CHECK (status IN ('PENDING', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT client_subscriptions_amount_chk
        CHECK (amount_minor > 0),
    CONSTRAINT fk_client_subscriptions_client
        FOREIGN KEY (client_id) REFERENCES clients (id) ON DELETE CASCADE,
    CONSTRAINT fk_client_subscriptions_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_client
    ON client_subscriptions (client_id);

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_client_active
    ON client_subscriptions (client_id, expires_at DESC)
 WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_payment
    ON client_subscriptions (payment_id) WHERE payment_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_client_subscriptions_status_expires
    ON client_subscriptions (status, expires_at);

DROP TRIGGER IF EXISTS update_client_subscriptions_updated_at ON client_subscriptions;
CREATE TRIGGER update_client_subscriptions_updated_at
    BEFORE UPDATE ON client_subscriptions
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
