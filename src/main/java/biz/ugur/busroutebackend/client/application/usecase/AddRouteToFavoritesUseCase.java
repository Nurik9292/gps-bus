package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Log4j2
@Service
public class AddRouteToFavoritesUseCase extends BaseUseCase<Mono<AddRouteToFavoritesUseCase.Command>, Boolean> {

    private final RouteFavoriteRepository routeFavoriteRepository;

    public AddRouteToFavoritesUseCase(RouteFavoriteRepository routeFavoriteRepository,
                                      CorrelationContextService correlationContextService,
                                      EventBus eventBus) {
        super(correlationContextService, eventBus);
        this.routeFavoriteRepository = routeFavoriteRepository;
    }


    @Override
    protected Mono<Boolean> process(Mono<Command> command) {
        return command.flatMap(this::processInternal);
    }

    @Override
    protected String getBoundContext() {
        return "client";
    }

    private Mono<Boolean> processInternal(Command command) {
        ClientId clientId = ClientId.of(command.clientId());
        BusRouteId routeId = BusRouteId.of(command.routeId());
       return correlationService.getCurrentCorrelationId().flatMap(correlationId -> {
           log.info("Adding route to Favorites CorrelationId: {} - ClientId: {} - RouteId: {}",
                   correlationId, clientId, routeId);

           return routeFavoriteRepository.existsByClientIdAndRouteId(clientId, routeId)
                   .flatMap(exists -> {
                       if (exists) {
                           return routeFavoriteRepository.deleteByClientIdAndRouteId(clientId, routeId)
                                   .thenReturn(false);
                       } else {
                           RouteFavorite favorite = RouteFavorite.create(clientId, routeId);
                           return routeFavoriteRepository.save(favorite).thenReturn(true);
                       }
                   });
       });
    }


    public record Command(String clientId, String routeId) {}
}