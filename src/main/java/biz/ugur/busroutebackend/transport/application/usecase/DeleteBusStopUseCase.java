package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class DeleteBusStopUseCase implements UseCase<String, Mono<Void>> {

    private final BusStopRepository busStopRepository;

    public DeleteBusStopUseCase(BusStopRepository busStopRepository) {
        this.busStopRepository = busStopRepository;
    }

    @Override
    public Mono<Void> execute(String stopId) {
        log.info("Deleting bus stop: {}", stopId);

        return busStopRepository.findById(BusStopId.of(stopId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus stop not found: " + stopId)))
                .flatMap(busStop -> {
                    // TODO: Проверить, используется ли остановка в активных маршрутах
                    // Если используется, можно деактивировать вместо удаления
                    return busStopRepository.deleteById(BusStopId.of(stopId));
                })
                .doOnSuccess(v -> log.info("Bus stop deleted successfully: {}", stopId))
                .doOnError(error -> log.error("Failed to delete bus stop: {}", stopId, error));
    }
}