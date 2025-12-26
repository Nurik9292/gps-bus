package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.ZoneId;
import java.time.ZonedDateTime;


@Service
@Slf4j
public class ClearAllImmediateAssignmentsUseCase extends BaseUseCase<Void, ClearAllImmediateAssignmentsUseCase.Result> {

    public static final ZoneId ASHGABAT_ZONE = ZoneId.of("Asia/Ashgabat");

    private final ImmediateRouteAssignmentRepository immediateAssignmentRepository;
    private final VehicleRepository vehicleRepository;

    public ClearAllImmediateAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository,
            VehicleRepository vehicleRepository) {
        super(correlationService, eventBus);
        this.immediateAssignmentRepository = immediateAssignmentRepository;
        this.vehicleRepository = vehicleRepository;
    }

    @Override
    protected Mono<Result> process(Void input) {
        ZonedDateTime now = ZonedDateTime.now(ASHGABAT_ZONE);

        return immediateAssignmentRepository.deactivateAll()
                .flatMap(immediateCount ->
                    vehicleRepository.clearAllRouteAssignments()
                        .map(vehicleCount -> {
                            log.info("Cleared {} immediate assignments and {} vehicle route assignments at {} (Ashgabat time)",
                                    immediateCount, vehicleCount, now);
                            return new Result(immediateCount + vehicleCount.intValue(), now.toString(), true, null);
                        })
                )
                .onErrorResume(error -> {
                    log.error("Failed to clear assignments: {}", error.getMessage());
                    return Mono.just(new Result(0, now.toString(), false, error.getMessage()));
                });
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    public record Result(
            int clearedCount,
            String clearedAt,
            boolean success,
            String errorMessage
    ) {}
}
