package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AddRouteToFavoritesUseCase implements UseCase<AddRouteToFavoritesUseCase.Command, Mono<Boolean>> {

    private final RouteFavoriteRepository routeFavoriteRepository;

    @Override
    public Mono<Boolean> execute(Command command) {
        ClientId clientId = ClientId.of(command.clientId());
        BusRouteId routeId = BusRouteId.of(command.routeId());

        return routeFavoriteRepository.existsByClientIdAndRouteId(clientId, routeId)
                .flatMap(exists -> {
                    if (exists) {
                        return routeFavoriteRepository.deleteByClientIdAndRouteId(clientId, routeId)
                                .thenReturn(false);
                    } else {
                        RouteFavorite favorite = new RouteFavorite(clientId, routeId);
                        return routeFavoriteRepository.save(favorite)
                                .thenReturn(true);
                    }
                });
    }

    public record Command(String clientId, String routeId) {}
}