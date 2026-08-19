ALTER TABLE ad_placements
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN external_service_id VARCHAR(36),
    ADD COLUMN external_ref VARCHAR(100);

ALTER TABLE ad_placements
    ADD CONSTRAINT chk_ad_placements_source CHECK (source IN ('MANUAL', 'EXTERNAL'));

ALTER TABLE ad_placements
    ADD CONSTRAINT chk_ad_placements_external_identity CHECK (
        (source = 'MANUAL' AND external_service_id IS NULL AND external_ref IS NULL)
        OR (source = 'EXTERNAL' AND external_service_id IS NOT NULL AND external_ref IS NOT NULL));

CREATE UNIQUE INDEX uq_ad_placements_external_ref
    ON ad_placements (external_service_id, external_ref)
    WHERE source = 'EXTERNAL';

CREATE INDEX idx_ad_placements_source
    ON ad_placements (source, display_order)
    WHERE status = 'ACTIVE';

COMMENT ON COLUMN ad_placements.source IS
    'Кто завёл размещение: MANUAL — админ через панель, EXTERNAL — внешний сервис через /api/v1/integration.';

COMMENT ON COLUMN ad_placements.external_service_id IS
    'Владелец записи для source=EXTERNAL: id внешнего сервиса из external_services. Только он может её менять и снимать.';

COMMENT ON COLUMN ad_placements.external_ref IS
    'Идентификатор баннера на стороне внешнего сервиса. Вместе с external_service_id даёт идемпотентность повторной передачи.';
