package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.CreateStop;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Random;

@Service
@Slf4j
public class CreateBusStopUseCase extends BaseUseCase<Mono<CreateStop>, StopData> {

    private final BusStopRepository busStopRepository;

    public CreateBusStopUseCase(BusStopRepository busStopRepository,
                                EventBus eventBus,
                                CorrelationContextService correlationService) {
        super(correlationService, eventBus);
        this.busStopRepository = busStopRepository;
    }

    @Override
    protected Mono<StopData> process(Mono<CreateStop> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<StopData> processInternal(CreateStop command) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.info("Creating bus stop: {} (EN: {}, TM: {}) Correlation - {}",
                            command.stopName(), command.nameEn(), command.nameTm(), correlationId);

                    return validateUniqueStopName(command.stopName())
                            .then(createBusStop(command))
                            .map(StopData::fromDomain)
                            .doOnSuccess(result -> log.info("Bus stop created: {}", result.stopName()))
                            .doOnError(error -> log.error("Failed to create bus stop: {}", command.stopName(), error));
                });
    }

    private Mono<BusStop> createBusStop(CreateStop command) {
        try {
            validateCoordinates(command.latitude(), command.longitude());

            BusStop busStop = new BusStop(
                    command.stopName(),
                    command.nameEn(),
                    command.nameTm(),
                    StopCode.generate(command.cityId(), new Random().nextInt(1000)),
                    command.latitude(),
                    command.longitude(),
                    command.isMajorStop(),
                    command.cityId()
            );

            return busStopRepository.save(busStop);
        } catch (Exception e) {
            return Mono.error(e);
        }
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
