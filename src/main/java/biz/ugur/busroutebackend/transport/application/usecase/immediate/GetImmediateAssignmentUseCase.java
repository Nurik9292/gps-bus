package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.immediate.ImmediateAssignmentData;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetImmediateAssignmentUseCase extends BaseUseCase<String, ImmediateAssignmentData> {

    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;
    private final BusRouteRepository busRouteRepository;

    public GetImmediateAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository,
            BusRouteRepository busRouteRepository) {
        super(correlationService, eventBus);
        this.immediateAssignmentRepository = immediateAssignmentRepository;
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Mono<ImmediateAssignmentData> process(String vehicleId) {
        return immediateAssignmentRepository.findActiveByVehicleId(new VehicleId(vehicleId))
                .filter(assignment -> !assignment.isExpired())
                .flatMap(assignment -> busRouteRepository.findById(assignment.getRouteId())
                        .map(route -> new ImmediateAssignmentData(
                                assignment.getId().getValue(),
                                assignment.getVehicleId().getValue(),
                                assignment.getRouteId().getValue(),
                                route.getRouteNumber(),
                                route.getRouteName(),
                                assignment.getAssignedBy(),
                                assignment.getReason(),
                                assignment.getAssignedAt(),
                                assignment.getExpiresAt(),
                                assignment.getIsActive()
                        )));
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }
}
