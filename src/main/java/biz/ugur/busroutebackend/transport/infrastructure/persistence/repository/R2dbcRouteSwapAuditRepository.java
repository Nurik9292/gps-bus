package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class R2dbcRouteSwapAuditRepository implements RouteSwapAuditRepository {

    private final DatabaseClient db;

    @Override
    public Mono<Void> logAssignmentChange(String vehicleId, String licensePlate,
                                          String previousRouteId, String newRouteId) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        INSERT INTO vehicle_assignment_log
                            (vehicle_id, license_plate, previous_route_id, new_route_id)
                        VALUES (:vehicleId, :licensePlate, :previousRouteId, :newRouteId)
                        """)
                .bind("vehicleId", vehicleId)
                .bind("licensePlate", licensePlate)
                .bind("newRouteId", newRouteId);
        spec = previousRouteId == null
                ? spec.bindNull("previousRouteId", String.class)
                : spec.bind("previousRouteId", previousRouteId);
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<Boolean> tryRecordVerdict(String licensePlate, String vehicleId,
                                          String assignedRouteNumber, String verdict, String detail,
                                          LocalDate operationalDate, String shift) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        INSERT INTO route_swap_verdicts
                            (license_plate, vehicle_id, assigned_route_number, verdict, detail,
                             operational_date, shift)
                        VALUES (:licensePlate, :vehicleId, :assignedRouteNumber, :verdict, :detail,
                                :operationalDate, :shift)
                        ON CONFLICT ON CONSTRAINT uq_route_swap_verdict DO NOTHING
                        """)
                .bind("licensePlate", licensePlate)
                .bind("verdict", verdict)
                .bind("operationalDate", operationalDate)
                .bind("shift", shift);
        spec = vehicleId == null
                ? spec.bindNull("vehicleId", String.class) : spec.bind("vehicleId", vehicleId);
        spec = assignedRouteNumber == null
                ? spec.bindNull("assignedRouteNumber", String.class)
                : spec.bind("assignedRouteNumber", assignedRouteNumber);
        spec = detail == null
                ? spec.bindNull("detail", String.class) : spec.bind("detail", detail);
        return spec.fetch().rowsUpdated().map(rows -> rows > 0);
    }
}
