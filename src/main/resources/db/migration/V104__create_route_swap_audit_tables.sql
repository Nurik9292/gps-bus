CREATE TABLE vehicle_assignment_log (
    id BIGSERIAL PRIMARY KEY,
    vehicle_id VARCHAR(36) NOT NULL,
    license_plate VARCHAR(20) NOT NULL,
    previous_route_id VARCHAR(36),
    new_route_id VARCHAR(36) NOT NULL,
    source VARCHAR(30) NOT NULL DEFAULT 'DETECTOR_OBSERVED',
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_vehicle_assignment_log_vehicle_time
    ON vehicle_assignment_log (vehicle_id, observed_at);
CREATE INDEX idx_vehicle_assignment_log_observed_at
    ON vehicle_assignment_log (observed_at);

COMMENT ON TABLE vehicle_assignment_log IS
    'Append-only лог фактических смен назначения борта (route_assignments вычищается кронами, ретро-истории нет). Пишется route-swap монитором по смене routeId между GPS-фиксами.';

CREATE TABLE route_swap_verdicts (
    id BIGSERIAL PRIMARY KEY,
    license_plate VARCHAR(20) NOT NULL,
    vehicle_id VARCHAR(36),
    assigned_route_number VARCHAR(10),
    verdict VARCHAR(30) NOT NULL,
    detail VARCHAR(300),
    operational_date DATE NOT NULL,
    shift VARCHAR(10) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_route_swap_verdict CHECK (verdict IN
        ('SWAP_SUSPECTED', 'SWAP_CONFIRMED', 'NO_AXIS_FITS', 'INTRA_FAMILY_MISMATCH')),
    CONSTRAINT uq_route_swap_verdict UNIQUE (license_plate, operational_date, shift, verdict)
);

CREATE INDEX idx_route_swap_verdicts_date
    ON route_swap_verdicts (operational_date);

COMMENT ON TABLE route_swap_verdicts IS
    'Вердикты route-swap детектора (shadow). UNIQUE-ключ даёт дедуп эпизодов в пределах смены, переживающий рестарт приложения.';
