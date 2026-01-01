package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class RouteAssignmentDataMapper {

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;

    public Mono<RouteAssignmentData> toRouteAssignmentData(RouteAssignment assignment) {
        if (assignment == null) {
            return Mono.empty();
        }

        Mono<VehicleInfo> vehicleInfoMono = vehicleRepository.findById(assignment.getVehicleId())
                .map(vehicle -> new VehicleInfo(
                        vehicle.getLicensePlate(),
                        vehicle.getDeviceId()
                ))
                .defaultIfEmpty(new VehicleInfo(null, null));

        Mono<RouteInfo> routeInfoMono = busRouteRepository.findById(assignment.getRouteId())
                .map(route -> new RouteInfo(
                        route.getRouteNumber(),
                        route.getRouteName()
                ))
                .defaultIfEmpty(new RouteInfo(null, null));

        return Mono.zip(vehicleInfoMono, routeInfoMono)
                .map(tuple -> {
                    VehicleInfo vehicleInfo = tuple.getT1();
                    RouteInfo routeInfo = tuple.getT2();

                    return new RouteAssignmentData(
                            assignment.getId().getValue(),
                            assignment.getVehicleId().getValue(),
                            vehicleInfo.licensePlate(),
                            vehicleInfo.deviceId(),
                            assignment.getRouteId().getValue(),
                            routeInfo.routeNumber(),
                            routeInfo.routeName(),
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
                });
    }

    private record VehicleInfo(String licensePlate, String deviceId) {}
    private record RouteInfo(String routeNumber, String routeName) {}
}
