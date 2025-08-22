package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Log4j2
public class CountVehicleUseCase extends BaseUseCase<Mono<Void>, CountVehicleUseCase.Response> {

    private final VehicleRepository vehicleRepository;

    public CountVehicleUseCase(VehicleRepository vehicleRepository,
                               CorrelationContextService correlationContextService,
                               EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.vehicleRepository = vehicleRepository;
    }


    @Override
    protected Mono<Response> process(Mono<Void> request) {
        return request.then(Mono.defer(this::processInternal));
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Response> processInternal() {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Count vehicle CorrelationId: {} ", correlationId);
            return vehicleRepository.count().map(Response::new);
        });
    }


    public record Response(Long count) {}
}
