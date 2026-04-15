-- =====================================================================================
-- V53: Business onboarding + advertising tariffs + ad placements
-- =====================================================================================
-- Introduces three bounded contexts:
--   • businesses        — companies applying to place ads in the app (lifecycle: PENDING/APPROVED/REJECTED/SUSPENDED)
--   • ad_tariffs        — pricing catalog (price × period × placement type)
--   • ad_placements     — concrete ad instances (business × tariff × creative × active window)
-- Payment linkage (FK to payments) is added in a later migration (V54) by the payment module.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- BUSINESSES
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS businesses (
    id                  VARCHAR(36) PRIMARY KEY,
    name                VARCHAR(200)    NOT NULL,
    business_type       VARCHAR(40)     NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    tax_number          VARCHAR(32),
    registration_number VARCHAR(64),

    -- Contact
    contact_person      VARCHAR(200),
    contact_phone       VARCHAR(32)     NOT NULL,
    contact_email       VARCHAR(200),
    website_url         VARCHAR(500),

    -- Address
    country             VARCHAR(100),
    city                VARCHAR(100),
    address_line        VARCHAR(500),

    -- Optional branding
    logo_url            TEXT,
    description         TEXT,

    -- Moderation
    rejection_reason    TEXT,
    approved_at         TIMESTAMP WITH TIME ZONE,
    approved_by_admin_id VARCHAR(36),
    suspended_at        TIMESTAMP WITH TIME ZONE,
    suspension_reason   TEXT,

    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT businesses_status_chk
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED')),
    CONSTRAINT businesses_type_chk
        CHECK (business_type IN ('RETAIL', 'RESTAURANT', 'SERVICE', 'ENTERTAINMENT',
                                  'EDUCATION', 'HEALTHCARE', 'TRANSPORT', 'OTHER'))
);

CREATE INDEX IF NOT EXISTS idx_businesses_status          ON businesses (status);
CREATE INDEX IF NOT EXISTS idx_businesses_type            ON businesses (business_type);
CREATE INDEX IF NOT EXISTS idx_businesses_created_at      ON businesses (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_businesses_tax_number      ON businesses (tax_number) WHERE tax_number IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_businesses_phone           ON businesses (contact_phone);

COMMENT ON TABLE  businesses                   IS 'Companies onboarded to run ads in the app (banners, push, popups).';
COMMENT ON COLUMN businesses.status            IS 'Lifecycle state: PENDING (just submitted) → APPROVED (visible, can buy placements) / REJECTED / SUSPENDED.';
COMMENT ON COLUMN businesses.tax_number        IS 'ИНН / tax identification number of the entity.';
COMMENT ON COLUMN businesses.approved_by_admin_id IS 'FK to admins.id of the moderator who approved the application.';


-- -------------------------------------------------------------------------------------
-- AD TARIFFS
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ad_tariffs (
    id                      VARCHAR(36) PRIMARY KEY,
    name                    VARCHAR(200) NOT NULL,
    description             TEXT,
    placement_type          VARCHAR(20) NOT NULL,
    period                  VARCHAR(10) NOT NULL,

    -- Pricing in minor currency units (e.g. tiyin, cents) to avoid float drift
    price_amount            BIGINT      NOT NULL,
    currency                VARCHAR(3)  NOT NULL DEFAULT 'TMT',

    -- Optional limits bundled with the tariff
    max_impressions         INTEGER,
    max_clicks              INTEGER,
    daily_impression_cap    INTEGER,

    is_active               BOOLEAN     NOT NULL DEFAULT true,
    display_order           INTEGER     NOT NULL DEFAULT 0,

    created_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at              TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version                 BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ad_tariffs_placement_type_chk
        CHECK (placement_type IN ('BANNER', 'PUSH', 'POPUP')),
    CONSTRAINT ad_tariffs_period_chk
        CHECK (period IN ('DAY', 'WEEK', 'MONTH', 'QUARTER', 'YEAR')),
    CONSTRAINT ad_tariffs_price_chk
        CHECK (price_amount >= 0),
    CONSTRAINT ad_tariffs_currency_chk
        CHECK (char_length(currency) = 3)
);

CREATE INDEX IF NOT EXISTS idx_ad_tariffs_active_type
    ON ad_tariffs (is_active, placement_type);
CREATE INDEX IF NOT EXISTS idx_ad_tariffs_display_order
    ON ad_tariffs (display_order ASC) WHERE is_active = true;

COMMENT ON TABLE  ad_tariffs                  IS 'Pricing catalog. Each row = one buyable ad package (price × placement × period).';
COMMENT ON COLUMN ad_tariffs.placement_type   IS 'Where the ad is shown: BANNER (in-app banner) / PUSH (notification) / POPUP (modal dialog).';
COMMENT ON COLUMN ad_tariffs.period           IS 'Billing/display period the price covers.';
COMMENT ON COLUMN ad_tariffs.price_amount     IS 'Price in minor currency units (tiyin). Divide by 100 for display.';


-- -------------------------------------------------------------------------------------
-- AD PLACEMENTS (concrete ads bought by a business)
-- -------------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ad_placements (
    id                  VARCHAR(36) PRIMARY KEY,
    business_id         VARCHAR(36) NOT NULL,
    tariff_id           VARCHAR(36) NOT NULL,

    placement_type      VARCHAR(20) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',

    -- Creative assets
    title               VARCHAR(200) NOT NULL,
    content             TEXT,
    image_url           TEXT,
    target_url          TEXT,
    cta_text            VARCHAR(100),

    -- Display window
    starts_at           TIMESTAMP WITH TIME ZONE,
    ends_at             TIMESTAMP WITH TIME ZONE,

    -- Runtime metrics
    impressions_count   BIGINT      NOT NULL DEFAULT 0,
    clicks_count        BIGINT      NOT NULL DEFAULT 0,

    -- Context targeting (excluded contexts: map.html). Stored as comma-separated tags
    -- for now; can be normalised to a join table when targeting grows complex.
    display_contexts    VARCHAR(500),

    display_order       INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version             BIGINT      NOT NULL DEFAULT 0,

    CONSTRAINT ad_placements_business_fk
        FOREIGN KEY (business_id) REFERENCES businesses (id) ON DELETE RESTRICT,
    CONSTRAINT ad_placements_tariff_fk
        FOREIGN KEY (tariff_id) REFERENCES ad_tariffs (id) ON DELETE RESTRICT,
    CONSTRAINT ad_placements_status_chk
        CHECK (status IN ('DRAFT', 'PENDING_PAYMENT', 'SCHEDULED', 'ACTIVE', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT ad_placements_placement_type_chk
        CHECK (placement_type IN ('BANNER', 'PUSH', 'POPUP')),
    CONSTRAINT ad_placements_window_chk
        CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX IF NOT EXISTS idx_ad_placements_business_id    ON ad_placements (business_id);
CREATE INDEX IF NOT EXISTS idx_ad_placements_tariff_id      ON ad_placements (tariff_id);
CREATE INDEX IF NOT EXISTS idx_ad_placements_status         ON ad_placements (status);
CREATE INDEX IF NOT EXISTS idx_ad_placements_active_window
    ON ad_placements (starts_at, ends_at) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_ad_placements_type_active
    ON ad_placements (placement_type, status);

COMMENT ON TABLE  ad_placements                 IS 'Concrete ads purchased by a business against a tariff, with creative + display window.';
COMMENT ON COLUMN ad_placements.status          IS 'DRAFT → PENDING_PAYMENT → SCHEDULED → ACTIVE → EXPIRED/CANCELLED.';
COMMENT ON COLUMN ad_placements.display_contexts IS 'Comma-separated contexts where the ad should be shown (home, trip-search, notifications). Excludes map.html.';
