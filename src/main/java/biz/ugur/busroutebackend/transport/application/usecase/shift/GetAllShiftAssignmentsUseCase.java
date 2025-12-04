package biz.ugur.busroutebackend.transport.application.usecase.shift;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.shift.ShiftAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.ShiftAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleShiftAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllShiftAssignmentsUseCase extends BaseUseCase<Mono<Void>, List<ShiftAssignmentData>> {

    private final VehicleShiftAssignmentRepository shiftAssignmentRepository;
    private final ShiftAssignmentDataMapper dataMapper;

    public GetAllShiftAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            VehicleShiftAssignmentRepository shiftAssignmentRepository,
            ShiftAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.shiftAssignmentRepository = shiftAssignmentRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<List<ShiftAssignmentData>> process(Mono<Void> request) {
        return shiftAssignmentRepository.findAllActive()
                .flatMap(dataMapper::toShiftAssignmentData)
                .collectList()
                .doOnSuccess(list -> log.debug("Retrieved {} shift assignments", list.size()));
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }
}
