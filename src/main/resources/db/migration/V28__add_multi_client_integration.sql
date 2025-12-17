ALTER TABLE external_services
ADD COLUMN can_manage_clients BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN external_services.can_manage_clients IS
    'If true, this service can register and manage multiple clients via integration API';

ALTER TABLE clients
ADD COLUMN created_by_service_id VARCHAR(36);

ALTER TABLE clients
ADD COLUMN external_user_id VARCHAR(255);

ALTER TABLE clients
ADD CONSTRAINT fk_clients_created_by_service
    FOREIGN KEY (created_by_service_id)
    REFERENCES external_services(id)
    ON DELETE SET NULL;

CREATE INDEX idx_clients_created_by_service
    ON clients(created_by_service_id)
    WHERE created_by_service_id IS NOT NULL;

CREATE UNIQUE INDEX idx_clients_service_external_user
    ON clients(created_by_service_id, external_user_id)
    WHERE created_by_service_id IS NOT NULL AND external_user_id IS NOT NULL;

CREATE INDEX idx_clients_external_user_id
    ON clients(external_user_id)
    WHERE external_user_id IS NOT NULL;

COMMENT ON COLUMN clients.created_by_service_id IS
    'External service that created this client (null for regular mobile/web clients)';

COMMENT ON COLUMN clients.external_user_id IS
    'User ID in the external service system (unique per service)';
