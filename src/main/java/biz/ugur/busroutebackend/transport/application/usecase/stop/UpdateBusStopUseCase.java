package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
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
public class UpdateBusStopUseCase extends BaseUseCase<Mono<UpdateStop>, StopData> {

    private final BusStopRepository busStopRepository;

    public UpdateBusStopUseCase(BusStopRepository busStopRepository,
                                EventBus eventBus,
                                CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.busStopRepository = busStopRepository;
    }

    @Override
    protected Mono<StopData> process(Mono<UpdateStop> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<StopData> processInternal(UpdateStop command) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Updating bus stop: {} (EN: {}, TM: {}) Correlation - {}",
                            command.stopName(), command.nameEn(), command.nameTm(), correlationId);

                    return busStopRepository.findById(BusStopId.of(command.stopId()))
                            .switchIfEmpty(Mono.error(new IllegalArgumentException("Bus stop not found: " + command.stopId())))
                            .flatMap(existingStop -> validateAndUpdate(existingStop, command))
                            .map(StopData::fromDomain)
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
            existingStop.updateInfo(
                    command.stopName(),
                    command.nameEn(),
                    command.nameTm(),
                    command.latitude(),
                    command.longitude(),
                    command.isActive(),
                    command.isMajorStop(),
                    "2"
            );

            return busStopRepository.save(existingStop);
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

    /**
     * Validate bus stop coordinates.
     * Now uses centralized TurkmenistanBounds for strict validation (bus stops are fixed infrastructure).
     *
     * @since 1.5.0 (Phase 3 - migrated to use TurkmenistanBounds)
     */
    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        // Use strict bounds for bus stops (they are fixed infrastructure)
        TurkmenistanBounds.validateStrictBounds(latitude, longitude);
    }
}