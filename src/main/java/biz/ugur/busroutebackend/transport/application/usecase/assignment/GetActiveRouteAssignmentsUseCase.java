package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class GetActiveRouteAssignmentsUseCase extends BaseUseCase<Mono<Void>, List<RouteAssignmentData>> {

    private static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final RouteAssignmentRepository assignmentRepository;
    private final RouteAssignmentDataMapper dataMapper;

    public GetActiveRouteAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            RouteAssignmentRepository assignmentRepository,
            RouteAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.assignmentRepository = assignmentRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<List<RouteAssignmentData>> process(Mono<Void> request) {
        return request.then(processInternal());
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<List<RouteAssignmentData>> processInternal() {
        LocalDate today = LocalDate.now(ASHGABAT_ZONE);
        ShiftType currentShift = ShiftType.getCurrentShift();

        log.debug("Getting active assignments for date={}, shift={}", today, currentShift);

        return assignmentRepository.findActiveByDateAndShift(today, currentShift)
                .filter(assignment -> !assignment.isExpired())
                .flatMap(dataMapper::toRouteAssignmentData)
                .collectList()
                .doOnSuccess(data -> log.debug("Retrieved {} active route assignments", data.size()))
                .doOnError(error -> log.error("Failed to retrieve active route assignments", error));
    }
}
