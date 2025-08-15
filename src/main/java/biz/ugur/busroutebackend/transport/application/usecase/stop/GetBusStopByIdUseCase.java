package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopDetail;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetBusStopByIdUseCase implements UseCase<Mono<String>, Mono<StopDetail>> {

    private final BusStopRepository busStopRepository;
    private final CorrelationContextService correlationService;

    @Override
    public Mono<StopDetail> execute(Mono<String> stopIdMono) {
        return correlationService.executeWithCorrelation(
                stopIdMono.flatMap(this::executeWithCorrelation),
                "mobile"
        );
    }

    private Mono<StopDetail> executeWithCorrelation(String stopId) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting stop by id - Correlation {}: stopId={}", correlationId, stopId);

                    return busStopRepository.findById(BusStopId.of(stopId))
                            .map(stop -> new StopDetail(
                                    stop.getId().getValue(),
                                    stop.getStopName(),
                                    stop.getNameEn(),
                                    stop.getNameTm(),
                                    stop.getStopCode() != null ? stop.getStopCode().getValue() : null,
                                    stop.getLatitude(),
                                    stop.getLongitude(),
                                    stop.getIsActive(),
                                    stop.getIsMajorStop(),
                                    stop.getServingRoutesCount(),
                                    stop.getCityId()
                            ))
                            .doOnSuccess(result -> log.debug("Retrieved stop: {}", result.stopName()))
                            .onErrorMap(error -> {
                                log.error("Failed to get stop by id {}: {}", stopId, error.getMessage());
                                return new RuntimeException("Stop not found: " + stopId);
                            });
                });
    }
}