CREATE TABLE IF NOT EXISTS payments (
    id                      VARCHAR(36) PRIMARY KEY,

    provider                VARCHAR(20)  NOT NULL,
    provider_order_id       VARCHAR(64),                -- orderId returned by sv_epg register.do
    order_number            VARCHAR(36)  NOT NULL,      -- our merchant-side unique id (UUID short)

    subject_type            VARCHAR(40)  NOT NULL,      -- e.g. 'AD_PLACEMENT'
    subject_id              VARCHAR(36)  NOT NULL,

    business_id             VARCHAR(36),

    amount_minor            BIGINT       NOT NULL,
    currency                VARCHAR(3)   NOT NULL DEFAULT 'TMT',

    status                  VARCHAR(20)  NOT NULL DEFAULT 'REGISTERED',
    form_url                TEXT,                        -- redirect target returned by provider
    return_url              TEXT NOT NULL,               -- where to send customer after payment

    initiated_at            TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at            TIMESTAMP WITH TIME ZONE,
    failed_at               TIMESTAMP WITH TIME ZONE,
    expires_at              TIMESTAMP WITH TIME ZONE,

    failure_code            VARCHAR(50),
    failure_message         VARCHAR(512),

    card_pan_masked         VARCHAR(32),
    card_expiration         VARCHAR(6),
    cardholder_name         VARCHAR(100),

    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT       NOT NULL DEFAULT 0,

    CONSTRAINT payments_provider_chk
        CHECK (provider IN ('RYSGAL', 'SENAGAT', 'BKB', 'HALK')),
    CONSTRAINT payments_status_chk
        CHECK (status IN ('REGISTERED', 'PREAUTH', 'COMPLETED', 'DECLINED',
                          'REVERSED', 'REFUNDED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT payments_subject_chk
        CHECK (subject_type IN ('AD_PLACEMENT')),
    CONSTRAINT payments_amount_chk
        CHECK (amount_minor > 0),
    CONSTRAINT payments_order_number_uniq UNIQUE (order_number)
);

CREATE INDEX IF NOT EXISTS idx_payments_subject
    ON payments (subject_type, subject_id);
CREATE INDEX IF NOT EXISTS idx_payments_business_id
    ON payments (business_id) WHERE business_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payments_provider_status
    ON payments (provider, status);
CREATE INDEX IF NOT EXISTS idx_payments_initiated_at
    ON payments (initiated_at DESC);
CREATE INDEX IF NOT EXISTS idx_payments_pending_sync
    ON payments (provider, initiated_at)
    WHERE status IN ('REGISTERED', 'PREAUTH');

COMMENT ON TABLE  payments IS
    'Payment orders placed through an external gateway. Links a business/ad_placement to a provider transaction.';
COMMENT ON COLUMN payments.provider          IS 'Payment provider: RYSGAL, SENAGAT (both sv_epg); BKB, HALK reserved for future.';
COMMENT ON COLUMN payments.provider_order_id IS 'orderId returned by sv_epg register.do (used for status/refund/reverse calls).';
COMMENT ON COLUMN payments.order_number      IS 'Merchant-side idempotency id sent to provider (unique).';
COMMENT ON COLUMN payments.amount_minor      IS 'Amount in minor currency units (divide by 100 for display).';
COMMENT ON COLUMN payments.status            IS 'Lifecycle: REGISTERED → (PREAUTH) → COMPLETED / DECLINED / REVERSED / REFUNDED / EXPIRED / CANCELLED.';


CREATE TABLE IF NOT EXISTS payment_callbacks_log (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          VARCHAR(36),
    provider            VARCHAR(20)  NOT NULL,
    kind                VARCHAR(20)  NOT NULL,          -- RETURN_URL / WEBHOOK / STATUS_POLL
    provider_order_id   VARCHAR(64),
    order_status_code   INTEGER,                         -- 0..6 from sv_epg
    error_code          INTEGER,
    error_message       VARCHAR(512),
    raw_payload         TEXT,                            -- full JSON/form body for forensics
    source_ip           VARCHAR(64),

    received_at         TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT payment_callbacks_kind_chk
        CHECK (kind IN ('RETURN_URL', 'WEBHOOK', 'STATUS_POLL', 'MANUAL_SYNC'))
);

CREATE INDEX IF NOT EXISTS idx_payment_callbacks_payment_id
    ON payment_callbacks_log (payment_id) WHERE payment_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_payment_callbacks_received_at
    ON payment_callbacks_log (received_at DESC);
CREATE INDEX IF NOT EXISTS idx_payment_callbacks_provider_order
    ON payment_callbacks_log (provider, provider_order_id);

COMMENT ON TABLE  payment_callbacks_log IS
    'Audit trail of all provider interactions (return-URL hits, webhooks, status polls). Essential for dispute resolution.';


ALTER TABLE ad_placements
    ADD COLUMN IF NOT EXISTS payment_id VARCHAR(36);

ALTER TABLE ad_placements
    ADD CONSTRAINT ad_placements_payment_fk
        FOREIGN KEY (payment_id) REFERENCES payments(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_ad_placements_payment_id
    ON ad_placements (payment_id) WHERE payment_id IS NOT NULL;

COMMENT ON COLUMN ad_placements.payment_id IS
    'FK to payments.id. Set when the placement is sent to payment; cleared on payment failure/cancel.';
