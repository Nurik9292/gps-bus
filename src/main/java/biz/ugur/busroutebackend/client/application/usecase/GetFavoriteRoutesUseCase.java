package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByIdUseCase;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Log4j2
@Service
public class GetFavoriteRoutesUseCase implements UseCase<String, Mono<List<RouteData>>> {

    private final CorrelationContextService correlationService;
    private final RouteFavoriteRepository routeFavoriteRepository;
    private final GetRouteByIdUseCase getRouteByIdUseCase;

    public GetFavoriteRoutesUseCase(CorrelationContextService correlationService,
                                    RouteFavoriteRepository routeFavoriteRepository,
                                    GetRouteByIdUseCase getRouteByIdUseCase) {
        this.correlationService = correlationService;
        this.routeFavoriteRepository = routeFavoriteRepository;
        this.getRouteByIdUseCase = getRouteByIdUseCase;
    }

    @Override
    public Mono<List<RouteData>> execute(String clientId) {
        return correlationService.executeWithCorrelation(loadFavoriteRoutes(clientId), "client");
    }

    private Mono<List<RouteData>> loadFavoriteRoutes(String clientId) {
        return routeFavoriteRepository.findByClientId(ClientId.of(clientId))
                .concatMap(favorite -> getRouteByIdUseCase
                        .execute(Mono.just(new GetRouteByIdUseCase.Query(favorite.getRouteId().getValue())))
                        .onErrorResume(error -> {
                            log.debug("Favorite route {} not loadable, skipping: {}",
                                    favorite.getRouteId().getValue(), error.toString());
                            return Mono.empty();
                        }))
                .collectList();
    }
}
