CREATE TABLE name_history (
    id           BIGSERIAL PRIMARY KEY,
    entity_kind  VARCHAR(10)  NOT NULL,
    entity_id    VARCHAR(36)  NOT NULL,
    field        VARCHAR(30)  NOT NULL,
    old_value    VARCHAR(300),
    new_value    VARCHAR(300),
    changed_by   VARCHAR(100),
    changed_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT name_history_kind_chk CHECK (entity_kind IN ('STOP','ROUTE')),
    CONSTRAINT name_history_entity_field_uniq UNIQUE (entity_kind, entity_id, field)
);

CREATE INDEX idx_name_history_entity ON name_history (entity_kind, entity_id);
