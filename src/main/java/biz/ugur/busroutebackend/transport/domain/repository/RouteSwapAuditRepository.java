package biz.ugur.busroutebackend.transport.domain.repository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface RouteSwapAuditRepository {

    record VerdictKey(String licensePlate, LocalDate operationalDate, String shift, String verdict) {
    }

    Mono<Void> logAssignmentChange(String vehicleId, String licensePlate,
                                   String previousRouteId, String newRouteId);

    Mono<Boolean> tryRecordVerdict(String licensePlate, String vehicleId,
                                   String assignedRouteNumber, String verdict, String detail,
                                   LocalDate operationalDate, String shift);

    Flux<VerdictKey> findVerdictKeysSince(LocalDate sinceDate);
}
