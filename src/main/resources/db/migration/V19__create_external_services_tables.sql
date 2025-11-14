CREATE TABLE external_services (
                                   id VARCHAR(36) PRIMARY KEY,
                                   name VARCHAR(255) NOT NULL,
                                   description TEXT,
                                   api_token VARCHAR(128) NOT NULL UNIQUE,
                                   is_active BOOLEAN NOT NULL DEFAULT true,
                                   allowed_endpoints TEXT[],
                                   rate_limit_per_minute INTEGER DEFAULT 100,
                                   last_used_at TIMESTAMP,
                                   created_by_admin_id VARCHAR(36) NOT NULL,
                                   created_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                   updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                   version BIGINT NOT NULL DEFAULT 0,

                                   CONSTRAINT fk_external_services_admin FOREIGN KEY (created_by_admin_id) REFERENCES admins(id)
);

CREATE INDEX idx_external_services_api_token ON external_services(api_token) WHERE is_active = true;
CREATE INDEX idx_external_services_is_active ON external_services(is_active);
CREATE INDEX idx_external_services_created_by ON external_services(created_by_admin_id);

CREATE TABLE external_service_api_logs (
                                           id VARCHAR(36) PRIMARY KEY,
                                           external_service_id VARCHAR(36) NOT NULL,
                                           endpoint VARCHAR(500) NOT NULL,
                                           http_method VARCHAR(10) NOT NULL,
                                           response_status INTEGER,
                                           response_time_ms INTEGER,
                                           ip_address VARCHAR(45),
                                           user_agent TEXT,
                                           error_message TEXT,
                                           created_at TIMESTAMP NOT NULL DEFAULT NOW(),

                                           CONSTRAINT fk_api_logs_external_service FOREIGN KEY (external_service_id) REFERENCES external_services(id) ON DELETE CASCADE
);

CREATE INDEX idx_api_logs_service_id ON external_service_api_logs(external_service_id);
CREATE INDEX idx_api_logs_created_at ON external_service_api_logs(created_at DESC);
CREATE INDEX idx_api_logs_endpoint ON external_service_api_logs(endpoint);

CREATE OR REPLACE FUNCTION update_external_services_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_external_services_updated_at
    BEFORE UPDATE ON external_services
    FOR EACH ROW
    EXECUTE FUNCTION update_external_services_updated_at();

COMMENT ON TABLE external_services IS 'External services that have API access to mobile endpoints';
COMMENT ON COLUMN external_services.api_token IS 'Unique API token for authentication (Bearer token)';
COMMENT ON COLUMN external_services.allowed_endpoints IS 'Array of allowed endpoint patterns. NULL means all endpoints allowed';
COMMENT ON COLUMN external_services.rate_limit_per_minute IS 'Maximum API calls per minute. NULL means no limit';

COMMENT ON TABLE external_service_api_logs IS 'Audit log of all API calls made by external services';
