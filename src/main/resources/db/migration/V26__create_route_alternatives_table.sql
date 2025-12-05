CREATE TABLE route_alternatives (
    id VARCHAR(36) PRIMARY KEY,
    primary_route_id VARCHAR(36) NOT NULL,
    alternative_route_id VARCHAR(36) NOT NULL,
    priority INTEGER NOT NULL DEFAULT 1,
    description VARCHAR(500),
    description_tm VARCHAR(500),
    description_en VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,

    CONSTRAINT fk_route_alternatives_primary_route
        FOREIGN KEY (primary_route_id) REFERENCES bus_routes(id) ON DELETE CASCADE,
    CONSTRAINT fk_route_alternatives_alternative_route
        FOREIGN KEY (alternative_route_id) REFERENCES bus_routes(id) ON DELETE CASCADE,

    CONSTRAINT chk_route_alternatives_no_self_reference
        CHECK (primary_route_id != alternative_route_id),

    CONSTRAINT uk_route_alternatives_pair
        UNIQUE (primary_route_id, alternative_route_id)
);

CREATE INDEX idx_route_alternatives_primary ON route_alternatives(primary_route_id);
CREATE INDEX idx_route_alternatives_alternative ON route_alternatives(alternative_route_id);
CREATE INDEX idx_route_alternatives_priority ON route_alternatives(primary_route_id, priority);

CREATE TRIGGER update_route_alternatives_updated_at
    BEFORE UPDATE ON route_alternatives
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE route_alternatives IS 'Links primary routes to their alternative routes (for closures, holidays, etc.)';
COMMENT ON COLUMN route_alternatives.primary_route_id IS 'The main route that has alternatives';
COMMENT ON COLUMN route_alternatives.alternative_route_id IS 'The alternative route to use when primary is inactive';
COMMENT ON COLUMN route_alternatives.priority IS 'Priority order: 1 = highest priority alternative';
COMMENT ON COLUMN route_alternatives.description IS 'Description of when to use this alternative (Russian)';
