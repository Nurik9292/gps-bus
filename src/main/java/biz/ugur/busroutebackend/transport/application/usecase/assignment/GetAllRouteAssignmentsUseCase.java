package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class GetAllRouteAssignmentsUseCase extends BaseUseCase<Mono<Void>, List<RouteAssignmentData>> {

    private final RouteAssignmentRepository assignmentRepository;
    private final RouteAssignmentDataMapper dataMapper;

    public GetAllRouteAssignmentsUseCase(
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
        return assignmentRepository.findAll()
                .flatMap(dataMapper::toRouteAssignmentData)
                .collectList()
                .doOnSuccess(data -> log.debug("Retrieved {} route assignments", data.size()))
                .doOnError(error -> log.error("Failed to retrieve route assignments", error));
    }
}
