package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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
    public Mono<Void> logOperatorAssignmentChange(String vehicleId, String licensePlate,
                                                  String previousRouteId, String newRouteId,
                                                  String source, String actor) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        INSERT INTO vehicle_assignment_log
                            (vehicle_id, license_plate, previous_route_id, new_route_id, source, actor)
                        VALUES (:vehicleId, :licensePlate, :previousRouteId, :newRouteId, :source, :actor)
                        """)
                .bind("vehicleId", vehicleId)
                .bind("licensePlate", licensePlate)
                .bind("newRouteId", newRouteId)
                .bind("source", source);
        spec = previousRouteId == null
                ? spec.bindNull("previousRouteId", String.class)
                : spec.bind("previousRouteId", previousRouteId);
        spec = actor == null
                ? spec.bindNull("actor", String.class) : spec.bind("actor", actor);
        return spec.fetch().rowsUpdated().then();
    }

    @Override
    public Mono<AssignmentChange> findLastOperatorReassign(String vehicleId, Instant since) {
        return db.sql("""
                        SELECT vehicle_id, license_plate, previous_route_id, new_route_id,
                               source, actor, observed_at
                          FROM vehicle_assignment_log
                         WHERE vehicle_id = :vehicleId
                           AND observed_at >= :since
                           AND source = 'OPERATOR_REASSIGN'
                         ORDER BY observed_at DESC
                         LIMIT 1
                        """)
                .bind("vehicleId", vehicleId)
                .bind("since", since)
                .map((row, meta) -> new AssignmentChange(
                        row.get("vehicle_id", String.class),
                        row.get("license_plate", String.class),
                        row.get("previous_route_id", String.class),
                        row.get("new_route_id", String.class),
                        row.get("source", String.class),
                        row.get("actor", String.class),
                        row.get("observed_at", OffsetDateTime.class).toInstant()))
                .one();
    }

    @Override
    public Mono<Boolean> tryRecordVerdict(String licensePlate, String vehicleId,
                                          String assignedRouteNumber, String verdict, String detail,
                                          String factualRouteNumbers,
                                          LocalDate operationalDate, String shift) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        INSERT INTO route_swap_verdicts
                            (license_plate, vehicle_id, assigned_route_number, verdict, detail,
                             factual_route_numbers, operational_date, shift)
                        VALUES (:licensePlate, :vehicleId, :assignedRouteNumber, :verdict, :detail,
                                :factualRouteNumbers, :operationalDate, :shift)
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
        spec = factualRouteNumbers == null
                ? spec.bindNull("factualRouteNumbers", String.class)
                : spec.bind("factualRouteNumbers", factualRouteNumbers);
        return spec.fetch().rowsUpdated().map(rows -> rows > 0);
    }

    @Override
    public Flux<VerdictRecord> findVerdicts(LocalDate fromDate, LocalDate toDate, String verdict, int limit) {
        String verdictFilter = verdict == null || verdict.isBlank() ? "" : " AND verdict = :verdict";
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        SELECT id, license_plate, vehicle_id, assigned_route_number, verdict, detail,
                               factual_route_numbers, operational_date, shift, created_at
                          FROM route_swap_verdicts
                         WHERE operational_date BETWEEN :fromDate AND :toDate
                        """ + verdictFilter + """
                         ORDER BY created_at DESC
                         LIMIT :limit
                        """)
                .bind("fromDate", fromDate)
                .bind("toDate", toDate)
                .bind("limit", limit);
        if (!verdictFilter.isEmpty()) {
            spec = spec.bind("verdict", verdict);
        }
        return spec.map((row, meta) -> new VerdictRecord(
                        row.get("id", Long.class),
                        row.get("license_plate", String.class),
                        row.get("vehicle_id", String.class),
                        row.get("assigned_route_number", String.class),
                        row.get("verdict", String.class),
                        row.get("detail", String.class),
                        row.get("factual_route_numbers", String.class),
                        row.get("operational_date", LocalDate.class),
                        row.get("shift", String.class),
                        row.get("created_at", OffsetDateTime.class).toInstant()))
                .all();
    }

    @Override
    public Flux<VerdictCount> countVerdictsByType(LocalDate operationalDate, String shift) {
        DatabaseClient.GenericExecuteSpec spec = db.sql("""
                        SELECT verdict, COUNT(*) AS cnt
                          FROM route_swap_verdicts
                         WHERE operational_date = :operationalDate
                           AND (:shift IS NULL OR shift = :shift)
                         GROUP BY verdict
                        """)
                .bind("operationalDate", operationalDate);
        spec = shift == null
                ? spec.bindNull("shift", String.class) : spec.bind("shift", shift);
        return spec.map((row, meta) -> new VerdictCount(
                        row.get("verdict", String.class),
                        row.get("cnt", Long.class)))
                .all();
    }

    @Override
    public Flux<VerdictKey> findVerdictKeysSince(LocalDate sinceDate) {
        return db.sql("""
                        SELECT license_plate, operational_date, shift, verdict
                          FROM route_swap_verdicts
                         WHERE operational_date >= :sinceDate
                        """)
                .bind("sinceDate", sinceDate)
                .map((row, meta) -> new VerdictKey(
                        row.get("license_plate", String.class),
                        row.get("operational_date", LocalDate.class),
                        row.get("shift", String.class),
                        row.get("verdict", String.class)))
                .all();
    }
}
