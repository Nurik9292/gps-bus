CREATE TABLE IF NOT EXISTS route_favorites (
                                               id VARCHAR(36) PRIMARY KEY,
    client_id VARCHAR(36) NOT NULL,
    route_id VARCHAR(36) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT DEFAULT 0,

    -- Foreign key constraints
    CONSTRAINT fk_route_favorites_client FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE CASCADE,
    CONSTRAINT fk_route_favorites_route FOREIGN KEY (route_id) REFERENCES bus_routes(id) ON DELETE CASCADE,

    -- Unique constraint to prevent duplicates
    CONSTRAINT uk_route_favorites_client_route UNIQUE (client_id, route_id)
    );

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_route_favorites_client_id ON route_favorites(client_id);
CREATE INDEX IF NOT EXISTS idx_route_favorites_route_id ON route_favorites(route_id);

-- Comments
COMMENT ON TABLE route_favorites IS 'Client favorite bus routes';
COMMENT ON COLUMN route_favorites.client_id IS 'Reference to client who favorited the route';
COMMENT ON COLUMN route_favorites.route_id IS 'Reference to favorited bus route';
