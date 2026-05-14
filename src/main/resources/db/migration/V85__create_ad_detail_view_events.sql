CREATE TABLE ad_detail_view_events (
    id            UUID         NOT NULL,
    placement_id  UUID         NOT NULL,
    occurred_at   TIMESTAMPTZ  NOT NULL,
    duration_ms   INTEGER      NOT NULL CHECK (duration_ms BETWEEN 0 AND 1800000),
    target_type   VARCHAR(32)  NULL,
    target_id     UUID         NULL,
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

DO $$
DECLARE
    start_month DATE := DATE_TRUNC('month', CURRENT_DATE)::DATE;
    i           INT;
    p_from      DATE;
    p_to        DATE;
    p_name      TEXT;
BEGIN
    FOR i IN 0..11 LOOP
        p_from := start_month + (i || ' months')::INTERVAL;
        p_to   := start_month + ((i + 1) || ' months')::INTERVAL;
        p_name := 'ad_detail_view_events_' || TO_CHAR(p_from, 'YYYY_MM');

        EXECUTE FORMAT(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF ad_detail_view_events FOR VALUES FROM (%L) TO (%L)',
            p_name, p_from, p_to
        );
        EXECUTE FORMAT(
            'CREATE INDEX IF NOT EXISTS ix_%s_placement ON %I (placement_id, occurred_at DESC)',
            p_name, p_name
        );
        EXECUTE FORMAT(
            'CREATE INDEX IF NOT EXISTS ix_%s_target ON %I (target_type, occurred_at DESC) WHERE target_type IS NOT NULL',
            p_name, p_name
        );
    END LOOP;
END $$;
