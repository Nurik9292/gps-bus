package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import biz.ugur.busroutebackend.transport.application.dto.shift.UpdateShiftAssignment;
import biz.ugur.busroutebackend.transport.application.mapper.ShiftAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.exceptions.ShiftAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.exceptions.ShiftAssignmentValidationException;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateShiftAssignmentUseCase extends BaseUseCase<Mono<UpdateShiftAssignmentUseCase.Command>, ShiftAssignmentData> {

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final BusRouteRepository busRouteRepository;
    private final ShiftAssignmentDataMapper dataMapper;

    public UpdateShiftAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            BusRouteRepository busRouteRepository,
            ShiftAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.busRouteRepository = busRouteRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<ShiftAssignmentData> process(Mono<Command> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<ShiftAssignmentData> processInternal(Command command) {
        VehicleShiftAssignmentId assignmentId = VehicleShiftAssignmentId.of(command.id());
        BusRouteId newRouteId = BusRouteId.of(command.update().routeId());

        return validateRouteExists(newRouteId)
                .then(shiftAssignmentRepository.findById(assignmentId))
                .switchIfEmpty(Mono.error(ShiftAssignmentNotFoundException.byId(command.id())))
                .map(assignment -> assignment.updateRoute(newRouteId))
                .flatMap(shiftAssignmentRepository::save)
                .flatMap(dataMapper::toShiftAssignmentData)
                .doOnSuccess(data -> log.info("Updated shift assignment: id={}, newRoute={}",
                        command.id(), command.update().routeId()))
                .doOnError(error -> log.error("Failed to update shift assignment", error));
    }

    private Mono<Void> validateRouteExists(BusRouteId routeId) {
        return busRouteRepository.findById(routeId)
                .switchIfEmpty(Mono.error(ShiftAssignmentValidationException.routeNotFound(routeId.getValue())))
                .then();
    }

    public record Command(String id, UpdateShiftAssignment update) {}
}
