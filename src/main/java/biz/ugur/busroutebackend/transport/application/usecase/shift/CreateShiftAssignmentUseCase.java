package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.shift.CreateShiftAssignment;
import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.ShiftAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.exceptions.ShiftAssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.model.VehicleShiftAssignment;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateShiftAssignmentUseCase extends BaseUseCase<Mono<CreateShiftAssignment>, ShiftAssignmentData> {

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;
    private final ShiftAssignmentDataMapper dataMapper;

    public CreateShiftAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository,
            ShiftAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<ShiftAssignmentData> process(Mono<CreateShiftAssignment> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<ShiftAssignmentData> processInternal(CreateShiftAssignment command) {
        VehicleId vehicleId = VehicleId.of(command.vehicleId());
        BusRouteId routeId = BusRouteId.of(command.routeId());
        ShiftType shiftType = ShiftType.fromString(command.shiftType());

        return validateVehicleExists(vehicleId)
                .then(validateRouteExists(routeId))
                .then(checkNoExistingAssignment(vehicleId, shiftType))
                .then(createAssignment(vehicleId, routeId, shiftType))
                .flatMap(dataMapper::toShiftAssignmentData)
                .doOnSuccess(data -> log.info("Created shift assignment: vehicle={}, route={}, shift={}",
                        command.vehicleId(), command.routeId(), command.shiftType()))
                .doOnError(error -> log.error("Failed to create shift assignment", error));
    }

    private Mono<Void> validateVehicleExists(VehicleId vehicleId) {
        return vehicleRepository.findById(vehicleId)
                .switchIfEmpty(Mono.error(ShiftAssignmentValidationException.vehicleNotFound(vehicleId.getValue())))
                .then();
    }

    private Mono<Void> validateRouteExists(BusRouteId routeId) {
        return busRouteRepository.findById(routeId)
                .switchIfEmpty(Mono.error(ShiftAssignmentValidationException.routeNotFound(routeId.getValue())))
                .then();
    }

    private Mono<Void> checkNoExistingAssignment(VehicleId vehicleId, ShiftType shiftType) {
        return shiftAssignmentRepository.existsByVehicleIdAndShiftType(vehicleId, shiftType)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(ShiftAssignmentValidationException.alreadyExists(
                                vehicleId.getValue(), shiftType.name()));
                    }
                    return Mono.empty();
                });
    }

    private Mono<VehicleShiftAssignment> createAssignment(VehicleId vehicleId, BusRouteId routeId, ShiftType shiftType) {
        VehicleShiftAssignment assignment = VehicleShiftAssignment.create(vehicleId, routeId, shiftType);
        return shiftAssignmentRepository.save(assignment);
    }
}
