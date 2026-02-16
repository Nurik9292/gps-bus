CREATE TABLE alias_suggestions (
    id              VARCHAR(36) PRIMARY KEY,
    entity_type     VARCHAR(20) NOT NULL,
    entity_id       VARCHAR(36) NOT NULL,
    suggested_alias VARCHAR(300) NOT NULL,
    language        VARCHAR(5)  NOT NULL DEFAULT 'ru',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    client_id       VARCHAR(36) NOT NULL,
    reviewer_id     VARCHAR(36),
    review_comment  TEXT,
    created_at      TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
    version         BIGINT      NOT NULL DEFAULT 1,

    CONSTRAINT chk_entity_type CHECK (entity_type IN ('PLACE', 'STREET')),
    CONSTRAINT chk_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_suggestion_client FOREIGN KEY (client_id) REFERENCES clients(id)
);

CREATE INDEX idx_suggestions_status ON alias_suggestions(status);
CREATE INDEX idx_suggestions_entity ON alias_suggestions(entity_type, entity_id);
CREATE INDEX idx_suggestions_client ON alias_suggestions(client_id);
