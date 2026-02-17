CREATE TABLE complaints (
    id              VARCHAR(36) PRIMARY KEY,
    type            VARCHAR(30) NOT NULL,
    title           VARCHAR(300) NOT NULL,
    description     TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'NEW',
    client_id       VARCHAR(36) NOT NULL,
    admin_comment   TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 1,

    CONSTRAINT chk_complaint_type CHECK (type IN ('BUS', 'ROUTE', 'ROUTE_PLANNING', 'GPS', 'STOP', 'DRIVER', 'APP', 'OTHER')),
    CONSTRAINT chk_complaint_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT fk_complaint_client FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE INDEX idx_complaints_status ON complaints(status);
CREATE INDEX idx_complaints_client ON complaints(client_id);
CREATE INDEX idx_complaints_type ON complaints(type);
CREATE INDEX idx_complaints_created_at ON complaints(created_at DESC);
