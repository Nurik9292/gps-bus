package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class CheckRouteNumberUseCase implements UseCase<Mono<String>, Mono<Boolean>> {

    private final BusRouteRepository busRouteRepository;
    private final CorrelationContextService correlationService;

    public CheckRouteNumberUseCase(BusRouteRepository busRouteRepository, CorrelationContextService correlationService) {
        this.busRouteRepository = busRouteRepository;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<Boolean> execute(Mono<String> routeNumber) {
        return correlationService.executeWithCorrelation(routeNumber.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<Boolean> executeWithCorrelation(String routeNumber) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Check availability route number CorrelationId - {} RouteNumber - {}", correlationId, routeNumber);

            return busRouteRepository.existsByRouteNumber(routeNumber);

        });
    }
}
