package biz.ugur.busroutebackend.transport.domain.repository;

import reactor.core.publisher.Mono;

import java.time.LocalDate;

public interface RouteSwapAuditRepository {

    Mono<Void> logAssignmentChange(String vehicleId, String licensePlate,
                                   String previousRouteId, String newRouteId);

    Mono<Boolean> tryRecordVerdict(String licensePlate, String vehicleId,
                                   String assignedRouteNumber, String verdict, String detail,
                                   LocalDate operationalDate, String shift);
}
