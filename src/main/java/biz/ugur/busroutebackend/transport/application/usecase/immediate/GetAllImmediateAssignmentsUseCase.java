package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@Slf4j
public class GetAllImmediateAssignmentsUseCase extends BaseUseCase<Void, GetAllImmediateAssignmentsUseCase.Result> {

    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;

    public GetAllImmediateAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository,
            VehicleRepository vehicleRepository,
            BusRouteRepository busRouteRepository) {
        super(correlationService, eventBus);
        this.immediateAssignmentRepository = immediateAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Mono<Result> process(Void input) {
        return immediateAssignmentRepository.findAllActive()
                .filter(assignment -> !assignment.isExpired())
                .flatMap(assignment ->
                    Mono.zip(
                        vehicleRepository.findById(assignment.getVehicleId()),
                        busRouteRepository.findById(assignment.getRouteId())
                    ).map(tuple -> {
                        var vehicle = tuple.getT1();
                        var route = tuple.getT2();
                        return new ImmediateAssignmentWithVehicle(
                                assignment.getId().getValue(),
                                assignment.getVehicleId().getValue(),
                                vehicle.getLicensePlate(),
                                assignment.getRouteId().getValue(),
                                route.getRouteNumber(),
                                route.getRouteName(),
                                assignment.getAssignedBy(),
                                assignment.getReason(),
                                assignment.getAssignedAt() != null ? assignment.getAssignedAt().toString() : null,
                                assignment.getExpiresAt() != null ? assignment.getExpiresAt().toString() : null,
                                assignment.getIsActive()
                        );
                    })
                )
                .collectList()
                .map(assignments -> {
                    log.debug("Found {} active immediate assignments", assignments.size());
                    return new Result(assignments, assignments.size());
                });
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record ImmediateAssignmentWithVehicle(
            String id,
            String vehicleId,
            String vehicleLicensePlate,
            String routeId,
            String routeNumber,
            String routeName,
            String assignedBy,
            String reason,
            String assignedAt,
            String expiresAt,
            Boolean isActive
    ) {}

    public record Result(
            List<ImmediateAssignmentWithVehicle> assignments,
            int totalCount
    ) {}
}
