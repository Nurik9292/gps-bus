package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteBusStopUseCase extends BaseUseCase<Mono<String>, Void> {

    private final BusStopRepository busStopRepository;

    public DeleteBusStopUseCase(BusStopRepository busStopRepository,
                                CorrelationContextService correlationService,
                                EventBus eventBus) {
        super(correlationService, eventBus);
        this.busStopRepository = busStopRepository;
    }

    @Override
    protected Mono<Void> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Void> processInternal(String stopId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Deleting bus stop: CorrelationId - {} StopId - {}", correlationId, stopId);
            return busStopRepository.findById(BusStopId.of(stopId))
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus stop not found: " + stopId)))
                    .flatMap(busStop -> {
                        // TODO: Проверить, используется ли остановка в активных маршрутах
                        // Если используется, можно деактивировать вместо удаления
                        return busStopRepository.deleteById(BusStopId.of(stopId));
                    })
                    .doOnSuccess(v -> log.info("Bus stop deleted successfully: {}", stopId))
                    .doOnError(error -> log.error("Failed to delete bus stop: {}", stopId, error));
        });
    }
}