package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class CheckRouteNumberUseCase extends BaseUseCase<Mono<String>, Boolean> {

    private final BusRouteRepository busRouteRepository;

    public CheckRouteNumberUseCase(BusRouteRepository busRouteRepository,
                                   CorrelationContextService correlationService,
                                   EventBus eventBus) {
        super(correlationService, eventBus);
        this.busRouteRepository = busRouteRepository;
    }


    @Override
    protected Mono<Boolean> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }


    private Mono<Boolean> processInternal(String routeNumber) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Check availability route number CorrelationId - {} RouteNumber - {}", correlationId, routeNumber);
            return busRouteRepository.existsByRouteNumber(routeNumber);
        });
    }
}
