package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.exceptions.ShiftAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteShiftAssignmentUseCase extends BaseUseCase<Mono<String>, Void> {

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;

    public DeleteShiftAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleShiftAssignmentRepository shiftAssignmentRepository) {
        super(correlationService, eventBus);
        this.shiftAssignmentRepository = shiftAssignmentRepository;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Void> processInternal(String id) {
        VehicleShiftAssignmentId assignmentId = VehicleShiftAssignmentId.of(id);

        return shiftAssignmentRepository.findById(assignmentId)
                .switchIfEmpty(Mono.error(ShiftAssignmentNotFoundException.byId(id)))
                .flatMap(assignment -> shiftAssignmentRepository.deleteById(assignmentId))
                .doOnSuccess(v -> log.info("Deleted shift assignment: id={}", id))
                .doOnError(error -> log.error("Failed to delete shift assignment", error));
    }
}
