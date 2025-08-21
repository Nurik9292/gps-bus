package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteBusRouteUseCase extends BaseUseCase<String, Void> {

    private final BusRouteRepository busRouteRepository;

    public DeleteBusRouteUseCase(BusRouteRepository busRouteRepository,
                                 CorrelationContextService correlationContextService,
                                 EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.busRouteRepository = busRouteRepository;
    }


    @Override
    protected Mono<Void> process(String routeId) {
        return processInternal(routeId);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Void> processInternal(String routeId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Deleting bus route CorrelationId: {} - RouteId: {}", correlationId, routeId);

            return busRouteRepository.findById(BusRouteId.of(routeId))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus route not found: " + routeId)))
                    .flatMap(busRoute -> {
                        // TODO: Проверить, есть ли активные автобусы на этом маршруте
                        // Если есть, можно деактивировать маршрут вместо удаления
                        return busRouteRepository.deleteById(BusRouteId.of(routeId));
                    })
                    .doOnSuccess(v -> log.info("Bus route deleted successfully: {}", routeId))
                    .doOnError(error -> log.error("Failed to delete bus route: {}", routeId, error));
        });
    }
}