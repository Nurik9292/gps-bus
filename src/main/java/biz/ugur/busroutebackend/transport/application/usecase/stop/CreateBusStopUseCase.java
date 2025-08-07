package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.CreateStop;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopResult;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

@Service
@Slf4j
public class CreateBusStopUseCase implements UseCase<Mono<CreateStop>, Mono<StopResult>> {

    private final BusStopRepository busStopRepository;
    private final EventBus eventBus;
    private final CorrelationContextService correlationService;

    public CreateBusStopUseCase(BusStopRepository busStopRepository,
                                EventBus eventBus,
                                CorrelationContextService correlationService) {
        this.busStopRepository = busStopRepository;
        this.eventBus = eventBus;
        this.correlationService = correlationService;
    }

    @Override
    public Mono<StopResult> execute(Mono<CreateStop> command) {
        return correlationService.executeWithCorrelation(command.flatMap(this::executeWithCorrelation), "admin");
    }

    private Mono<StopResult> executeWithCorrelation(CreateStop command) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Creating bus stop: {} (EN: {}, TM: {}) Correlation - {}",
                            command.stopName(), command.nameEn(), command.nameTm(), correlationId);

                    return validateUniqueStopName(command.stopName())
                            .then(validateUniqueStopCode(command.stopCode()))
                            .then(createBusStop(command))
                            .map(StopResult::fromDomain)
                            .doOnSuccess(result -> log.info("Bus stop created: {}", result.stopName()))
                            .doOnError(error -> log.error("Failed to create bus stop: {}", command.stopName(), error));
                });
    }

    private Mono<BusStop> createBusStop(CreateStop command) {
        try {
            validateCoordinates(command.latitude(), command.longitude());

            String resolvedCode = resolveStopCode(command);

            BusStop busStop = new BusStop(
                    command.stopName(),
                    command.nameEn(),
                    command.nameTm(),
                    resolvedCode,
                    command.latitude(),
                    command.longitude(),
                    command.isMajorStop(),
                    command.cityId()
            );

            return busStopRepository.save(busStop)
                    .doOnNext(savedStop -> {
                        savedStop.getUncommittedEvents().forEach(eventBus::publish);
                        savedStop.markEventsAsCommitted();
                    });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    private String resolveStopCode(CreateStop command) {
        return command.stopCode() != null
                ? command.stopCode()
                : generateStopCode(command.stopName());
    }

    private Mono<Void> validateUniqueStopName(String stopName) {
        return busStopRepository.existsByStopName(stopName)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Bus stop with this name already exists"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<Void> validateUniqueStopCode(String stopCode) {
        if (stopCode == null) return Mono.empty();

        return busStopRepository.existsByStopCode(stopCode)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalArgumentException("Bus stop with this code already exists"));
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

    private String generateStopCode(String stopName) {
        return stopName.replaceAll("\\s+", "_")
                .replaceAll("[^a-zA-Z0-9_]", "")
                .toUpperCase() + "_" + System.currentTimeMillis() % 10000;
    }
}
