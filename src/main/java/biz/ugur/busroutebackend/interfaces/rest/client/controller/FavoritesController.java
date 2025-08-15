package biz.ugur.busroutebackend.interfaces.rest.client.controller;

import biz.ugur.busroutebackend.client.application.usecase.AddStopToFavoritesUseCase;
import biz.ugur.busroutebackend.client.application.usecase.AddRouteToFavoritesUseCase;
import biz.ugur.busroutebackend.client.application.usecase.UpdateClientActivityUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Objects;

@RestController
@RequestMapping("/client/favorites")
@RequiredArgsConstructor
@Slf4j
public class FavoritesController {

    private final AddStopToFavoritesUseCase addStopToFavoritesUseCase;
    private final AddRouteToFavoritesUseCase addRouteToFavoritesUseCase;
    private final UpdateClientActivityUseCase updateClientActivityUseCase;

    @PostMapping("/stops/{stopId}")
    public Mono<ResponseEntity<FavoriteResponse>> toggleStopFavorite(
            @PathVariable String stopId,
            Authentication authentication) {

        String clientId = authentication.getName();
        log.info("Toggle stop favorite: clientId={}, stopId={}", clientId, stopId);

        return updateClientActivity(clientId)
                .then(addStopToFavoritesUseCase.execute(new AddStopToFavoritesUseCase.Command(clientId, stopId)))
                .map(added -> ResponseEntity.ok(new FavoriteResponse(
                        added,
                        added ? "Stop added to favorites" : "Stop removed from favorites"
                )))
                .doOnSuccess(response -> log.info("Stop favorite toggled: clientId={}, stopId={}, added={}",
                        clientId, stopId, Objects.requireNonNull(response.getBody()).added()))
                .onErrorResume(error -> {
                    log.error("Failed to toggle stop favorite: {}", error.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(new FavoriteResponse(false, error.getMessage())));
                });
    }

    @PostMapping("/routes/{routeId}")
    public Mono<ResponseEntity<FavoriteResponse>> toggleRouteFavorite(
            @PathVariable String routeId,
            Authentication authentication) {

        String clientId = authentication.getName();
        log.info("Toggle route favorite: clientId={}, routeId={}", clientId, routeId);

        return updateClientActivity(clientId)
                .then(addRouteToFavoritesUseCase.execute(new AddRouteToFavoritesUseCase.Command(clientId, routeId)))
                .map(added -> ResponseEntity.ok(new FavoriteResponse(
                        added,
                        added ? "Route added to favorites" : "Route removed from favorites"
                )))
                .doOnSuccess(response -> log.info("Route favorite toggled: clientId={}, routeId={}, added={}",
                        clientId, routeId, Objects.requireNonNull(response.getBody()).added()))
                .onErrorResume(error -> {
                    log.error("Failed to toggle route favorite: {}", error.getMessage());
                    return Mono.just(ResponseEntity.badRequest()
                            .body(new FavoriteResponse(false, error.getMessage())));
                });
    }

    private Mono<Void> updateClientActivity(String clientId) {
        return updateClientActivityUseCase.execute(new UpdateClientActivityUseCase.Command(clientId));
    }

    public record FavoriteResponse(Boolean added, String message) {}
}