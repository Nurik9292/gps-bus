package biz.ugur.busroutebackend.transport.application.usecase.assignment;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.assignment.RouteAssignmentData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteAssignmentDataMapper;
import biz.ugur.busroutebackend.transport.domain.exceptions.RouteAssignmentNotFoundException;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAssignmentId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetRouteAssignmentByIdUseCase extends BaseUseCase<Mono<String>, RouteAssignmentData> {

    private final RouteAssignmentRepository assignmentRepository;
    private final RouteAssignmentDataMapper dataMapper;

    public GetRouteAssignmentByIdUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            RouteAssignmentRepository assignmentRepository,
            RouteAssignmentDataMapper dataMapper) {
        super(correlationService, eventBus);
        this.assignmentRepository = assignmentRepository;
        this.dataMapper = dataMapper;
    }

    @Override
    protected Mono<RouteAssignmentData> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteAssignmentData> processInternal(String id) {
        RouteAssignmentId assignmentId = RouteAssignmentId.of(id);

        return assignmentRepository.findById(assignmentId)
                .switchIfEmpty(Mono.error(() -> RouteAssignmentNotFoundException.byId(id)))
                .flatMap(dataMapper::toRouteAssignmentData)
                .doOnSuccess(data -> log.debug("Retrieved route assignment: id={}", id))
                .doOnError(error -> log.error("Failed to retrieve route assignment: id={}", id, error));
    }
}
