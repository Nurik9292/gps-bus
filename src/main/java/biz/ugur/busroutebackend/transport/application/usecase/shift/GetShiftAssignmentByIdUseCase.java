package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.ShiftAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.exceptions.ShiftAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleShiftAssignmentId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetShiftAssignmentByIdUseCase extends BaseUseCase<Mono<String>, ShiftAssignmentData> {

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftAssignmentDataMapper dataMapper;

    public GetShiftAssignmentByIdUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            ShiftAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<ShiftAssignmentData> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<ShiftAssignmentData> processInternal(String id) {
        VehicleShiftAssignmentId assignmentId = VehicleShiftAssignmentId.of(id);

        return shiftAssignmentRepository.findById(assignmentId)
                .switchIfEmpty(Mono.error(ShiftAssignmentNotFoundException.byId(id)))
                .flatMap(dataMapper::toShiftAssignmentData);
    }
}
