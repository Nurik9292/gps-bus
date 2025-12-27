package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.event.VehicleRouteAutoChangedEvent;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.ImmediateRouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteSource;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.application.dto.immediate.ImmediateAssignmentData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;


@Service
@Slf4j
public class CreateImmediateAssignmentUseCase extends BaseUseCase<CreateImmediateAssignmentUseCase.Command, ImmediateAssignmentData> {

    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;
    private final VehicleRepository vehicleRepository;
    private final BusRouteRepository busRouteRepository;

    public CreateImmediateAssignmentUseCase(
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
    protected Mono<ImmediateAssignmentData> process(Command command) {
        VehicleId vehicleId = new VehicleId(command.vehicleId());
        BusRouteId routeId = new BusRouteId(command.routeId());

        return Mono.zip(
                        vehicleRepository.findById(vehicleId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Vehicle not found: " + command.vehicleId()))),
                        busRouteRepository.findById(routeId)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Route not found: " + command.routeId())))
                )
                .flatMap(tuple -> {
                    Vehicle vehicle = tuple.getT1();
                    BusRoute route = tuple.getT2();

                    return immediateAssignmentRepository.deactivateByVehicleId(vehicleId)
                            .then(Mono.defer(() -> {
                                ImmediateRouteAssignment assignment = ImmediateRouteAssignment.create(
                                        vehicleId,
                                        routeId,
                                        command.assignedBy(),
                                        command.reason(),
                                        command.expiresAt()
                                );

                                return immediateAssignmentRepository.save(assignment)
                                        .flatMap(saved -> updateVehicle(vehicle, route, command.assignedBy(), command.reason())
                                                .thenReturn(saved));
                            }));
                })
                .flatMap(saved -> Mono.zip(
                        busRouteRepository.findById(saved.getRouteId()),
                        vehicleRepository.findById(saved.getVehicleId())
                ).map(routeAndVehicle -> toData(
                        saved,
                        routeAndVehicle.getT2().getLicensePlate(),
                        routeAndVehicle.getT1().getRouteNumber(),
                        routeAndVehicle.getT1().getRouteName()
                )));
    }

    private Mono<Vehicle> updateVehicle(Vehicle vehicle, BusRoute route, String assignedBy, String reason) {
        String previousRouteId = vehicle.getAssignedRouteId() != null
                ? vehicle.getAssignedRouteId().getValue() : null;
        String previousRouteNumber = vehicle.getRouteNumber();

        Vehicle updatedVehicle = vehicle.toBuilder()
                .assignedRouteId(route.getId())
                .routeNumber(route.getRouteNumber())
                .routeSource(RouteSource.MANUAL)
                .routeConfidence(100)
                .assignedBy(assignedBy)
                .manualAssignmentReason(reason)
                .build();

        return vehicleRepository.save(updatedVehicle)
                .doOnSuccess(saved -> {
                    VehicleRouteAutoChangedEvent event = new VehicleRouteAutoChangedEvent(
                            saved.getId().getValue(),
                            saved.getLicensePlate(),
                            previousRouteId,
                            previousRouteNumber,
                            route.getId().getValue(),
                            route.getRouteNumber(),
                            RouteSource.MANUAL,
                            100,
                            "Immediate assignment by " + assignedBy + (reason != null ? ": " + reason : "")
                    );
                    eventBus.publish(event);

                    log.info("Immediate assignment created: vehicle={}, route={}, by={}",
                            saved.getLicensePlate(), route.getRouteNumber(), assignedBy);
                });
    }

    private ImmediateAssignmentData toData(ImmediateRouteAssignment assignment, String vehicleLicensePlate, String routeNumber, String routeName) {
        return new ImmediateAssignmentData(
                assignment.getId().getValue(),
                assignment.getVehicleId().getValue(),
                vehicleLicensePlate,
                assignment.getRouteId().getValue(),
                routeNumber,
                routeName,
                assignment.getAssignedBy(),
                assignment.getReason(),
                assignment.getAssignedAt(),
                assignment.getExpiresAt(),
                assignment.getIsActive()
        );
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record Command(
            String vehicleId,
            String routeId,
            String assignedBy,
            String reason,
            Instant expiresAt
    ) {}
}
