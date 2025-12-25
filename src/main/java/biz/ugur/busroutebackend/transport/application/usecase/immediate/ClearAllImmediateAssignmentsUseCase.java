package biz.ugur.busroutebackend.transport.application.usecase.immediate;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.ImmediateRouteAssignmentRepository;
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

    public ClearAllImmediateAssignmentsUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus,
            ImmediateRouteAssignmentRepository immediateAssignmentRepository) {
        super(correlationService, eventBus);
        this.immediateAssignmentRepository = immediateAssignmentRepository;
    }

    @Override
    protected Mono<Result> process(Void input) {
        ZonedDateTime now = ZonedDateTime.now(ASHGABAT_ZONE);

        return immediateAssignmentRepository.deactivateAll()
                .map(count -> new Result(count, now.toString(), true, null))
                .doOnSuccess(result ->
                    log.info("Cleared {} immediate assignments at {} (Ashgabat time)",
                            result.clearedCount(), result.clearedAt()))
                .onErrorResume(error -> {
                    log.error("Failed to clear immediate assignments: {}", error.getMessage());
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
