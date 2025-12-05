package biz.ugur.busroutebackend.transport.application.usecase.routealternative;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.application.dto.RouteAlternativeDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAlternative;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAlternativeRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class CreateRouteAlternativeUseCase extends BaseUseCase<CreateRouteAlternativeUseCase.Request, RouteAlternativeDTO> {

    private final RouteAlternativeRepository routeAlternativeRepository;
    private final BusRouteRepository busRouteRepository;

    public CreateRouteAlternativeUseCase(
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
        BusRouteId primaryRouteId = BusRouteId.of(request.primaryRouteId);
        BusRouteId alternativeRouteId = BusRouteId.of(request.alternativeRouteId);

        return validateRoutes(primaryRouteId, alternativeRouteId)
                .then(checkDuplicateLink(primaryRouteId, alternativeRouteId))
                .then(createAndSave(request, primaryRouteId, alternativeRouteId))
                .flatMap(this::enrichWithRouteInfo);
    }

    @Override
    protected String getBoundContext() {
        return "transport";
    }

    private Mono<Void> validateRoutes(BusRouteId primaryRouteId, BusRouteId alternativeRouteId) {
        Mono<BusRoute> primaryCheck = busRouteRepository.findById(primaryRouteId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Primary route not found: " + primaryRouteId.getValue())));

        Mono<BusRoute> alternativeCheck = busRouteRepository.findById(alternativeRouteId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Alternative route not found: " + alternativeRouteId.getValue())));

        return Mono.when(primaryCheck, alternativeCheck);
    }

    private Mono<Void> checkDuplicateLink(BusRouteId primaryRouteId, BusRouteId alternativeRouteId) {
        return routeAlternativeRepository.existsByPrimaryAndAlternative(primaryRouteId, alternativeRouteId)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new IllegalStateException(
                                "Alternative link already exists between these routes"));
                    }
                    return Mono.empty();
                });
    }

    private Mono<RouteAlternative> createAndSave(Request request, BusRouteId primaryRouteId, BusRouteId alternativeRouteId) {
        RouteAlternative alternative = RouteAlternative.create(
                primaryRouteId,
                alternativeRouteId,
                request.priority,
                request.description,
                request.descriptionTm,
                request.descriptionEn
        );

        return routeAlternativeRepository.save(alternative)
                .doOnSuccess(saved -> log.info("Created route alternative: primary={}, alternative={}",
                        primaryRouteId.getValue(), alternativeRouteId.getValue()));
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
            String primaryRouteId,
            String alternativeRouteId,
            Integer priority,
            String description,
            String descriptionTm,
            String descriptionEn
    ) {}
}
