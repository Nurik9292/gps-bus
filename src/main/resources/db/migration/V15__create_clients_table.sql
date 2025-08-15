CREATE TABLE IF NOT EXISTS clients (
                                       id VARCHAR(36) PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    phone VARCHAR(20) NOT NULL UNIQUE,
    otp VARCHAR(5),
    otp_verify BOOLEAN DEFAULT FALSE,
    platform VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'INACTIVE',
    last_activity TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    access_token TEXT,
    refresh_token TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0
    );

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_clients_phone ON clients(phone);
CREATE INDEX IF NOT EXISTS idx_clients_status ON clients(status);
CREATE INDEX IF NOT EXISTS idx_clients_last_activity ON clients(last_activity);
CREATE INDEX IF NOT EXISTS idx_clients_platform ON clients(platform);

-- Comments
COMMENT ON TABLE clients IS 'Client accounts for mobile app users';
COMMENT ON COLUMN clients.id IS 'Unique client identifier (UUID)';
COMMENT ON COLUMN clients.phone IS 'Phone number in Turkmenistan format (+993...)';
COMMENT ON COLUMN clients.otp IS 'One-time password for verification (5 digits)';
COMMENT ON COLUMN clients.otp_verify IS 'Whether OTP has been verified';
COMMENT ON COLUMN clients.platform IS 'Client platform: ANDROID, IOS, WEB, MOBILE_WEB';
COMMENT ON COLUMN clients.status IS 'Client status: INACTIVE, ACTIVE, SUSPENDED, BLOCKED';
COMMENT ON COLUMN clients.last_activity IS 'Last activity timestamp for tracking';