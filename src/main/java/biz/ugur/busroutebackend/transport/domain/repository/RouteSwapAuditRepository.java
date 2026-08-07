package biz.ugur.busroutebackend.transport.domain.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface RouteSwapAuditRepository {

    record VerdictKey(String licensePlate, LocalDate operationalDate, String shift, String verdict) {
    }

    record VerdictRecord(Long id, String licensePlate, String vehicleId, String assignedRouteNumber,
                         String verdict, String detail, LocalDate operationalDate, String shift,
                         java.time.Instant createdAt) {
    }

    record VerdictCount(String verdict, long count) {
    }

    Mono<Void> logAssignmentChange(String vehicleId, String licensePlate,
                                   String previousRouteId, String newRouteId);

    Mono<Boolean> tryRecordVerdict(String licensePlate, String vehicleId,
                                   String assignedRouteNumber, String verdict, String detail,
                                   LocalDate operationalDate, String shift);

    Flux<VerdictKey> findVerdictKeysSince(LocalDate sinceDate);

    Flux<VerdictRecord> findVerdicts(LocalDate fromDate, LocalDate toDate, String verdict, int limit);

    Flux<VerdictCount> countVerdictsByType(LocalDate operationalDate, String shift);
}
