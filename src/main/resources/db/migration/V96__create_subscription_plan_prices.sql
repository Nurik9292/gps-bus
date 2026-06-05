CREATE TABLE IF NOT EXISTS subscription_plan_prices (
    id           VARCHAR(16)              NOT NULL PRIMARY KEY,
    amount_minor BIGINT                   NOT NULL,
    currency     VARCHAR(3)               NOT NULL DEFAULT 'TMT',
    updated_by   VARCHAR(100),
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version      BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT subscription_plan_prices_period_chk
        CHECK (id IN ('MONTHLY', 'YEARLY')),
    CONSTRAINT subscription_plan_prices_amount_chk
        CHECK (amount_minor > 0)
);

INSERT INTO subscription_plan_prices (id, amount_minor, currency)
VALUES ('MONTHLY', 400, 'TMT'),
       ('YEARLY', 4000, 'TMT')
ON CONFLICT (id) DO NOTHING;

DROP TRIGGER IF EXISTS update_subscription_plan_prices_updated_at ON subscription_plan_prices;
CREATE TRIGGER update_subscription_plan_prices_updated_at
    BEFORE UPDATE ON subscription_plan_prices
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
