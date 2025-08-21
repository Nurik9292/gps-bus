package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Log4j2
public class ActiveCountVehicleUseCase implements UseCase<Mono<Void>, Mono<ActiveCountVehicleUseCase.Response>> {

    private final VehicleRepository vehicleRepository;
    private final CorrelationContextService correlationContextService;

    public ActiveCountVehicleUseCase(VehicleRepository vehicleRepository, CorrelationContextService correlationContextService) {
        this.vehicleRepository = vehicleRepository;
        this.correlationContextService = correlationContextService;
    }

    @Override
    public Mono<Response> execute(Mono<Void> voidMono) {
        return correlationContextService.executeWithCorrelation(this.executeWithCorrelation(), "transport");
    }

    private Mono<Response> executeWithCorrelation() {
        return correlationContextService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Count vehicle CorrelationId: {} ", correlationId);
            return vehicleRepository.countActiveVehicles().map(Response::new);
        });
    }

    public record Response(Long count) {}
}
