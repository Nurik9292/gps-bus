package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopResult;
import biz.ugur.busroutebackend.transport.application.dto.stop.UpdateStop;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
@Slf4j
public class UpdateBusStopUseCase implements UseCase<Mono<UpdateStop>, Mono<StopResult>> {

    private final BusStopRepository busStopRepository;
    private final EventBus eventBus;
    private final CorrelationContextService correlationService;

    public UpdateBusStopUseCase(BusStopRepository busStopRepository,
                                EventBus eventBus,
                                CorrelationContextService correlationService) {
        this.busStopRepository = busStopRepository;
        this.eventBus = eventBus;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<StopResult> execute(Mono<UpdateStop> command) {
        return correlationService.executeWithCorrelation(command.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<StopResult> executeWithCorrelation(UpdateStop command) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Updating bus stop: {} (EN: {}, TM: {}) Correlation - {}",
                            command.stopName(), command.nameEn(), command.nameTm(), correlationId);

                    return busStopRepository.findById(BusStopId.of(command.stopId()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus stop not found: " + command.stopId())))
                            .flatMap(existingStop -> validateAndUpdate(existingStop, command))
                            .map(StopResult::fromDomain)
                            .doOnSuccess(result -> log.info("Bus stop updated successfully: {}", result.stopName()))
                            .doOnError(error -> log.error("Failed to update bus stop: {}", command.stopId(), error));
                });
    }

    private Mono<BusStop> validateAndUpdate(BusStop existingStop, UpdateStop command) {
        return validateUniqueStopNameIfChanged(existingStop, command.stopName())
                .then(updateBusStop(existingStop, command));
    }

    private Mono<BusStop> updateBusStop(BusStop existingStop, UpdateStop command) {
        try {
            if (coordinatesChanged(existingStop, command)) {
                validateCoordinates(command.latitude(), command.longitude());
            }
            System.out.println("Updating bus stop: " + command);
            existingStop.updateInfo(
                    command.stopName(),
                    command.nameEn(),
                    command.nameTm(),
                    command.latitude(),
                    command.longitude(),
                    command.isActive(),
                    command.isMajorStop()
            );

            return busStopRepository.save(existingStop)
                    .doOnNext(savedStop -> {
                        savedStop.getUncommittedEvents().forEach(eventBus::publish);
                        savedStop.markEventsAsCommitted();
                    });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private boolean coordinatesChanged(BusStop existingStop, UpdateStop command) {
        return !existingStop.getLatitude().equals(command.latitude()) ||
                !existingStop.getLongitude().equals(command.longitude());
    }

    private Mono<Void> validateUniqueStopNameIfChanged(BusStop existingStop, String newStopName) {
        if (existingStop.getStopName().equals(newStopName)) {
            return Mono.empty();
        }

        return busStopRepository.existsByStopName(newStopName)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Bus stop with this name already exists"));
                    }
                    return Mono.empty();
                });
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude.compareTo(new BigDecimal("35.1")) < 0 ||
                latitude.compareTo(new BigDecimal("42.8")) > 0) {
            throw new IllegalArgumentException("Latitude must be within Turkmenistan bounds (35.1-42.8)");
        }

        if (longitude.compareTo(new BigDecimal("52.5")) < 0 ||
                longitude.compareTo(new BigDecimal("66.7")) > 0) {
            throw new IllegalArgumentException("Longitude must be within Turkmenistan bounds (52.5-66.7)");
        }
    }
}