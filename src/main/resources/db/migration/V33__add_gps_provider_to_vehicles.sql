ALTER TABLE vehicles
    ADD COLUMN IF NOT EXISTS gps_provider VARCHAR(20) DEFAULT 'CHINA' NOT NULL;

CREATE INDEX IF NOT EXISTS idx_vehicles_gps_provider ON vehicles(gps_provider);

CREATE INDEX IF NOT EXISTS idx_vehicles_active_provider ON vehicles(gps_provider, is_active)
    WHERE is_active = true;

ALTER TABLE vehicles
    ADD CONSTRAINT chk_vehicles_gps_provider
        CHECK (gps_provider IN ('CHINA', 'TUGDK'));

COMMENT ON COLUMN vehicles.gps_provider IS
    'GPS data provider for this vehicle. CHINA = China GPS API (default), TUGDK = TUGDK Government GPS API';
