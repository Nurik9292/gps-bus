package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class FindRouteByIdUseCase extends BaseUseCase<String, RouteData> {

    private final BusRouteRepository busRouteRepository;
    private final RouteDataMapper routeDataMapper;

    public FindRouteByIdUseCase(BusRouteRepository busRouteRepository,
                                CorrelationContextService correlationContextService,
                                EventBus eventBus,
                                RouteDataMapper routeDataMapper) {
        super(correlationContextService, eventBus);
        this.busRouteRepository = busRouteRepository;
        this.routeDataMapper = routeDataMapper;
    }

    @Override
    protected Mono<RouteData> process(String request) {
        return processInternal(request);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteData> processInternal(String routeId) {
        return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
            log.info("Find route by id Correlation ID: {} RouteId: {}", correlationId, routeId);
            return busRouteRepository.findById(BusRouteId.of(routeId)).flatMap(routeDataMapper::toRouteData);
        });
    }
}
