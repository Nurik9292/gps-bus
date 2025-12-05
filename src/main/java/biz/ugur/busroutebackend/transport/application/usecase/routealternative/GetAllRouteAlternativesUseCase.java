package biz.ugur.busroutebackend.transport.application.usecase.routealternative;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseFluxUseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteAlternativeDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class GetAllRouteAlternativesUseCase extends BaseFluxUseCase<Void, RouteAlternativeDTO> {

    private final RouteAlternativeRepository routeAlternativeRepository;
    private final BusRouteRepository busRouteRepository;

    public GetAllRouteAlternativesUseCase(
            RouteAlternativeRepository routeAlternativeRepository,
            BusRouteRepository busRouteRepository,
            CorrelationContextService correlationService,
            EventBus eventBus) {
        super(correlationService, eventBus);
        this.routeAlternativeRepository = routeAlternativeRepository;
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Flux<RouteAlternativeDTO> process(Void request) {
        return routeAlternativeRepository.findAll()
                .flatMap(this::enrichWithRouteInfo)
                .doOnComplete(() -> log.debug("Fetched all route alternatives"));
    }

    @Override
    protected String getBoundedContext() {
        return "transport";
    }

    private Mono<RouteAlternativeDTO> enrichWithRouteInfo(RouteAlternative alternative) {
        Mono<BusRoute> primaryRoute = busRouteRepository.findById(alternative.getPrimaryRouteId())
                .defaultIfEmpty(createEmptyRoute(alternative.getPrimaryRouteId().getValue()));
        Mono<BusRoute> alternativeRoute = busRouteRepository.findById(alternative.getAlternativeRouteId())
                .defaultIfEmpty(createEmptyRoute(alternative.getAlternativeRouteId().getValue()));

        return Mono.zip(primaryRoute, alternativeRoute)
                .map(tuple -> new RouteAlternativeDTO(
                        alternative.getId().getValue(),
                        alternative.getPrimaryRouteId().getValue(),
                        tuple.getT1().getRouteNumber(),
                        tuple.getT1().getRouteName(),
                        alternative.getAlternativeRouteId().getValue(),
                        tuple.getT2().getRouteNumber(),
                        tuple.getT2().getRouteName(),
                        tuple.getT2().getIsActive(),
                        alternative.getPriority(),
                        alternative.getDescription(),
                        alternative.getDescriptionTm(),
                        alternative.getDescriptionEn(),
                        alternative.getCreatedAt(),
                        alternative.getUpdatedAt()
                ));
    }

    private BusRoute createEmptyRoute(String id) {
        return BusRoute.restore(
                BusRouteId.of(id),
                "?", "Unknown", null, null, null,
                false, null, null, null, null, null, null,
                null, null, null
        );
    }
}
