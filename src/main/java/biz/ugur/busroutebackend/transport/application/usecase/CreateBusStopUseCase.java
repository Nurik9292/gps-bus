package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.BusStopCreateRequest;
import biz.ugur.busroutebackend.transport.application.dto.BusStopResponse;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateBusStopUseCase implements UseCase<BusStopCreateRequest, Mono<BusStopResponse>> {

    private final BusStopRepository busStopRepository;
    private final EventBus eventBus;

    public CreateBusStopUseCase(BusStopRepository busStopRepository, EventBus eventBus) {
        this.busStopRepository = busStopRepository;
        this.eventBus = eventBus;
    }

    @Override
    public Mono<BusStopResponse> execute(BusStopCreateRequest request) {
        log.info("Creating bus stop: {}", request.getStopName());

        if (request.getStopCode() != null && !request.getStopCode().trim().isEmpty()) {
            return busStopRepository.existsByStopCode(request.getStopCode())
                    .flatMap(exists -> {
                        if (exists) {
                            return Mono.error(new IllegalArgumentException("Stop code already exists: " + request.getStopCode()));
                        }
                        return createBusStop(request);
                    });
        } else {
            return createBusStop(request);
        }
    }

    private Mono<BusStopResponse> createBusStop(BusStopCreateRequest request) {
        BusStop busStop = new BusStop(
                request.getStopName(),
                request.getStopCode(),
                request.getLatitude(),
                request.getLongitude()
        );

        if (request.getIsMajorStop() != null) {
            busStop = new BusStop(
                    busStop.getId(),
                    busStop.getStopName(),
                    busStop.getStopCode(),
                    busStop.getLatitude(),
                    busStop.getLongitude(),
                    request.getIsActive(),
                    request.getIsMajorStop(),
                    request.getHasShelter()
            );
        }

        return busStopRepository.save(busStop)
                .doOnNext(savedStop -> {
                    savedStop.getUncommittedEvents().forEach(eventBus::publish);
                    savedStop.markEventsAsCommitted();
                })
                .map(this::toResponse)
                .doOnSuccess(response -> log.info("Bus stop created successfully: {}", response.getStopName()))
                .doOnError(error -> log.error("Failed to create bus stop: {}", request.getStopName(), error));
    }

    private BusStopResponse toResponse(BusStop busStop) {
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
}