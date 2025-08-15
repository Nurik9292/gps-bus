CREATE TABLE IF NOT EXISTS stop_favorites (
                                              id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(36) NOT NULL,
    stop_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,

    CONSTRAINT fk_stop_favorites_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    CONSTRAINT fk_stop_favorites_stop FOREIGN KEY (stop_id) REFERENCES bus_stops(id) ON DELETE CASCADE,

    CONSTRAINT uk_stop_favorites_client_stop UNIQUE (client_id, stop_id)
    );

CREATE INDEX IF NOT EXISTS idx_stop_favorites_client_id ON stop_favorites(client_id);
CREATE INDEX IF NOT EXISTS idx_stop_favorites_stop_id ON stop_favorites(stop_id);

COMMENT ON TABLE stop_favorites IS 'Client favorite bus stops';
COMMENT ON COLUMN stop_favorites.client_id IS 'Reference to client who favorited the stop';
COMMENT ON COLUMN stop_favorites.stop_id IS 'Reference to favorited bus stop';