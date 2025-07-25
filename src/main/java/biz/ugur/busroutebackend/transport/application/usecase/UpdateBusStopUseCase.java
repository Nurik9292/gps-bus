package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusStopCreateRequest;
import biz.ugur.busroutebackend.transport.application.dto.BusStopResponse;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateBusStopUseCase implements UseCase<UpdateBusStopUseCase.Request, Mono<BusStopResponse>> {

    private final BusStopRepository busStopRepository;
    private final EventBus eventBus;

    public UpdateBusStopUseCase(BusStopRepository busStopRepository, EventBus eventBus) {
        this.busStopRepository = busStopRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<BusStopResponse> execute(Request request) {
        log.info("Updating bus stop: {}", request.stopId);

        return busStopRepository.findById(BusStopId.of(request.stopId))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus stop not found: " + request.stopId)))
                .flatMap(busStop -> {
                    var updatedStop = new biz.ugur.busroutebackend.transport.domain.model.BusStop(
                            busStop.getId(),
                            request.updateRequest.getStopName() != null ?
                                    request.updateRequest.getStopName() : busStop.getStopName(),
                            request.updateRequest.getStopCode() != null ?
                                    request.updateRequest.getStopCode() : busStop.getStopCode(),
                            request.updateRequest.getLatitude() != null ?
                                    request.updateRequest.getLatitude() : busStop.getLatitude(),
                            request.updateRequest.getLongitude() != null ?
                                    request.updateRequest.getLongitude() : busStop.getLongitude(),
                            request.updateRequest.getIsActive() != null ?
                                    request.updateRequest.getIsActive() : busStop.getIsActive(),
                            request.updateRequest.getIsMajorStop() != null ?
                                    request.updateRequest.getIsMajorStop() : busStop.getIsMajorStop(),
                            request.updateRequest.getHasShelter() != null ?
                                    request.updateRequest.getHasShelter() : busStop.getHasShelter()
                    );

                    return busStopRepository.save(updatedStop);
                })
                .doOnNext(savedStop -> {
                    savedStop.getUncommittedEvents().forEach(eventBus::publish);
                    savedStop.markEventsAsCommitted();
                })
                .map(this::toResponse)
                .doOnSuccess(response -> log.info("Bus stop updated successfully: {}", response.getStopName()))
                .doOnError(error -> log.error("Failed to update bus stop: {}", request.stopId, error));
    }

    private BusStopResponse toResponse(biz.ugur.busroutebackend.transport.domain.model.BusStop busStop) {
        return new BusStopResponse(
                busStop.getId().getValue(),
                busStop.getStopName(),
                busStop.getStopCode(),
                busStop.getLatitude(),
                busStop.getLongitude(),
                busStop.getIsActive(),
                busStop.getIsMajorStop(),
                busStop.getHasShelter(),
                busStop.getServingRoutesCount()
        );
    }

    public record Request(String stopId, BusStopCreateRequest updateRequest) {}
}