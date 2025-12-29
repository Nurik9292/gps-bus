package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RouteAssignmentDataMapper {

    public Mono<RouteAssignmentData> toRouteAssignmentData(RouteAssignment assignment) {
        if (assignment == null) {
            return Mono.empty();
        }

        RouteAssignmentData data = new RouteAssignmentData(
                assignment.getId().getValue(),
                assignment.getVehicleId().getValue(),
                assignment.getRouteId().getValue(),
                assignment.getEffectiveDate(),
                assignment.getShiftType().name(),
                assignment.getAssignedBy(),
                assignment.getReason(),
                assignment.getExpiresAt(),
                assignment.getIsActive(),
                assignment.isForCurrentShift(),
                assignment.isExpired(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt(),
                assignment.getVersion()
        );

        return Mono.just(data);
    }
}
