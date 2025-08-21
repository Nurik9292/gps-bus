package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopDetail;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetBusStopByIdUseCase extends BaseUseCase<Mono<String>, StopDetail> {

    private final BusStopRepository busStopRepository;

    public GetBusStopByIdUseCase(BusStopRepository busStopRepository,
                                 CorrelationContextService correlationContextService,
                                 EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.busStopRepository = busStopRepository;
    }


    @Override
    protected Mono<StopDetail> process(Mono<String> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<StopDetail> processInternal(String stopId) {
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