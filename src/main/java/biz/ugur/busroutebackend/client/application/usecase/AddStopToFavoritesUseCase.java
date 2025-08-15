package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.StopFavorite;
import biz.ugur.busroutebackend.client.domain.repository.StopFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.shared.application.UseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AddStopToFavoritesUseCase implements UseCase<AddStopToFavoritesUseCase.Command, Mono<Boolean>> {

    private final StopFavoriteRepository stopFavoriteRepository;

    @Override
    public Mono<Boolean> execute(Command command) {
        ClientId clientId = ClientId.of(command.clientId());
        BusStopId stopId = BusStopId.of(command.stopId());

        return stopFavoriteRepository.existsByClientIdAndStopId(clientId, stopId)
                .flatMap(exists -> {
                    if (exists) {
                        return stopFavoriteRepository.deleteByClientIdAndStopId(clientId, stopId)
                                .thenReturn(false);
                    } else {
                        StopFavorite favorite = new StopFavorite(clientId, stopId);
                        return stopFavoriteRepository.save(favorite)
                                .thenReturn(true);
                    }
                });
    }

    public record Command(String clientId, String stopId) {}
}