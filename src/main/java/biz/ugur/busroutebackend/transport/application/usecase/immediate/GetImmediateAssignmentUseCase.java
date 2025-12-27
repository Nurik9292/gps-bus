package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.immediate.ImmediateAssignmentData;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetImmediateAssignmentUseCase extends BaseUseCase<String, ImmediateAssignmentData> {

    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;
    private final BusRouteRepository busRouteRepository;
    private final VehicleRepository vehicleRepository;

    public GetImmediateAssignmentUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository,
            BusRouteRepository busRouteRepository,
            VehicleRepository vehicleRepository) {
        super(correlationService, eventBus);
        this.immediateAssignmentRepository = immediateAssignmentRepository;
        this.busRouteRepository = busRouteRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    protected Mono<ImmediateAssignmentData> process(String vehicleId) {
        VehicleId vId = new VehicleId(vehicleId);
        return immediateAssignmentRepository.findActiveByVehicleId(vId)
                .filter(assignment -> !assignment.isExpired())
                .flatMap(assignment -> Mono.zip(
                        busRouteRepository.findById(assignment.getRouteId()),
                        vehicleRepository.findById(assignment.getVehicleId())
                ).map(tuple -> new ImmediateAssignmentData(
                        assignment.getId().getValue(),
                        assignment.getVehicleId().getValue(),
                        tuple.getT2().getLicensePlate(),
                        assignment.getRouteId().getValue(),
                        tuple.getT1().getRouteNumber(),
                        tuple.getT1().getRouteName(),
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
