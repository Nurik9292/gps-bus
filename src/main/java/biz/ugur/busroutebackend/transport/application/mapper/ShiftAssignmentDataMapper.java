package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import biz.ugur.busroutebackend.transport.application.dto.shift.VehicleShiftsData;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ShiftAssignmentDataMapper {

    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;

    public Mono<ShiftAssignmentData> toShiftAssignmentData(VehicleShiftAssignment assignment) {
        return Mono.zip(
                vehicleRepository.findById(assignment.getVehicleId()),
                busRouteRepository.findById(assignment.getRouteId())
        ).map(tuple -> {
            Vehicle vehicle = tuple.getT1();
            BusRoute route = tuple.getT2();
            return mapToData(assignment, vehicle, route);
        }).switchIfEmpty(Mono.defer(() -> {
            // Return data even if vehicle or route not found
            return Mono.just(mapToData(assignment, null, null));
        }));
    }

    public Mono<VehicleShiftsData> toVehicleShiftsData(VehicleId vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .flatMap(vehicle -> {
                    Mono<ShiftAssignmentData> firstShiftMono = shiftAssignmentRepository
                            .findByVehicleIdAndShiftType(vehicleId, ShiftType.FIRST)
                            .flatMap(this::toShiftAssignmentData)
                            .defaultIfEmpty(createEmptyShiftData(ShiftType.FIRST));

                    Mono<ShiftAssignmentData> secondShiftMono = shiftAssignmentRepository
                            .findByVehicleIdAndShiftType(vehicleId, ShiftType.SECOND)
                            .flatMap(this::toShiftAssignmentData)
                            .defaultIfEmpty(createEmptyShiftData(ShiftType.SECOND));

                    return Mono.zip(firstShiftMono, secondShiftMono)
                            .map(tuple -> new VehicleShiftsData(
                                    vehicle.getId().getValue(),
                                    vehicle.getLicensePlate(),
                                    vehicle.getDeviceId(),
                                    tuple.getT1(),
                                    tuple.getT2()
                            ));
                });
    }

    private ShiftAssignmentData mapToData(VehicleShiftAssignment assignment, Vehicle vehicle, BusRoute route) {
        ShiftType shiftType = assignment.getShiftType();
        return new ShiftAssignmentData(
                assignment.getId().getValue(),
                assignment.getVehicleId().getValue(),
                vehicle != null ? vehicle.getLicensePlate() : null,
                vehicle != null ? vehicle.getDeviceId() : null,
                assignment.getRouteId().getValue(),
                route != null ? route.getRouteNumber() : null,
                route != null ? route.getRouteName() : null,
                shiftType.name(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                assignment.getIsActive(),
                assignment.isCurrentlyActive(),
                assignment.getCreatedAt(),
                assignment.getUpdatedAt()
        );
    }

    private ShiftAssignmentData createEmptyShiftData(ShiftType shiftType) {
        return new ShiftAssignmentData(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                shiftType.name(),
                shiftType.getStartTime(),
                shiftType.getEndTime(),
                false,
                false,
                null,
                null
        );
    }
}
