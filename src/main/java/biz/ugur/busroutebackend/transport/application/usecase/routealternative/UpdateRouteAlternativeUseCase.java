package biz.ugur.busroutebackend.transport.application.usecase.routealternative;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteAlternativeDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateRouteAlternativeUseCase extends BaseUseCase<UpdateRouteAlternativeUseCase.Request, RouteAlternativeDTO> {

    private final RouteAlternativeRepository routeAlternativeRepository;
    private final BusRouteRepository busRouteRepository;

    public UpdateRouteAlternativeUseCase(
            RouteAlternativeRepository routeAlternativeRepository,
            BusRouteRepository busRouteRepository,
            CorrelationContextService correlationService,
            EventBus eventBus) {
        super(correlationService, eventBus);
        this.routeAlternativeRepository = routeAlternativeRepository;
        this.busRouteRepository = busRouteRepository;
    }

    @Override
    protected Mono<RouteAlternativeDTO> process(Request request) {
        RouteAlternativeId id = RouteAlternativeId.of(request.id);

        return routeAlternativeRepository.findById(id)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Route alternative not found: " + request.id)))
                .map(existing -> existing.update(
                        request.priority,
                        request.description,
                        request.descriptionTm,
                        request.descriptionEn
                ))
                .flatMap(routeAlternativeRepository::save)
                .flatMap(this::enrichWithRouteInfo)
                .doOnSuccess(dto -> log.info("Updated route alternative: {}", request.id));
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<RouteAlternativeDTO> enrichWithRouteInfo(RouteAlternative alternative) {
        Mono<BusRoute> primaryRoute = busRouteRepository.findById(alternative.getPrimaryRouteId());
        Mono<BusRoute> alternativeRoute = busRouteRepository.findById(alternative.getAlternativeRouteId());

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

    public record Request(
            String id,
            Integer priority,
            String description,
            String descriptionTm,
            String descriptionEn
    ) {}
}
