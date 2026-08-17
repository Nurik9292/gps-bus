ALTER TABLE route_swap_verdicts
    ADD COLUMN factual_route_numbers VARCHAR(60);

COMMENT ON COLUMN route_swap_verdicts.factual_route_numbers IS
    'Номера маршрутов-кандидатов фактической езды через "/" (семейство осей для SWAP_SUSPECTED, номер реестра для PROVIDER_MISMATCH). NULL для вердиктов без фактического кандидата.';

ALTER TABLE vehicle_assignment_log
    ADD COLUMN actor VARCHAR(100);

COMMENT ON COLUMN vehicle_assignment_log.actor IS
    'Админ, выполнивший ручное переназначение (source OPERATOR_REASSIGN / OPERATOR_REVERT). NULL у наблюдений детектора.';
