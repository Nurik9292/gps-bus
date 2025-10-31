package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopDetail;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.repository.R2dbcRouteStopRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetBusStopByIdUseCase extends BaseUseCase<Mono<GetBusStopByIdUseCase.Query>, StopData> {

    private final BusStopRepository busStopRepository;
    private final R2dbcRouteStopRepository  r2dbcRouteStopRepository;

    public GetBusStopByIdUseCase(BusStopRepository busStopRepository,
                                 CorrelationContextService correlationContextService,
                                 EventBus eventBus,
                                 R2dbcRouteStopRepository r2dbcRouteStopRepository) {
        super(correlationContextService, eventBus);
        this.busStopRepository = busStopRepository;
        this.r2dbcRouteStopRepository = r2dbcRouteStopRepository;
    }


    @Override
    protected Mono<StopData> process(Mono<Query> request) {
        return request.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<StopData> processInternal(Query query) {
        return correlationService.getCurrentCorrelationId()
                .flatMap(correlationId -> {
                    log.debug("Getting stop by id - Correlation {}: stopId={}", correlationId, query.stopId);
                    return busStopRepository.findById(BusStopId.of(query.stopId))
                            .map(StopData::fromDomain)
                            .doOnSuccess(result -> log.debug("Retrieved stop: {}", result.stopName()))
                            .onErrorMap(error -> {
                                log.error("Failed to get stop by id {}: {}", query.stopId, error.getMessage());
                                return new RuntimeException("Stop not found: " + query.stopId);
                            });
                });
    }

    public record Query(String stopId) {}
}