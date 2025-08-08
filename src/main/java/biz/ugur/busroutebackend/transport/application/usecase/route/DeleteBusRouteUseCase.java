package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteBusRouteUseCase implements UseCase<String, Mono<Void>> {

    private final BusRouteRepository busRouteRepository;

    public DeleteBusRouteUseCase(BusRouteRepository busRouteRepository) {
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    public Mono<Void> execute(String routeId) {
        log.info("Deleting bus route: {}", routeId);

        return busRouteRepository.findById(BusRouteId.of(routeId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus route not found: " + routeId)))
                .flatMap(busRoute -> {
                    // TODO: Проверить, есть ли активные автобусы на этом маршруте
                    // Если есть, можно деактивировать маршрут вместо удаления
                    return busRouteRepository.deleteById(BusRouteId.of(routeId));
                })
                .doOnSuccess(v -> log.info("Bus route deleted successfully: {}", routeId))
                .doOnError(error -> log.error("Failed to delete bus route: {}", routeId, error));
    }
}