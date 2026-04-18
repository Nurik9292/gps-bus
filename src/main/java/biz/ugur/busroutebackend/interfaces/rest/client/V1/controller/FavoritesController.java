package biz.ugur.busroutebackend.interfaces.rest.client.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.AddRouteToFavoritesUseCase;
import biz.ugur.busroutebackend.client.application.usecase.AddStopToFavoritesUseCase;
import biz.ugur.busroutebackend.client.application.usecase.UpdateClientActivityUseCase;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.shared.infrastructure.web.BaseController;
import org.springframework.context.MessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import static biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig.V1_CLIENT_FAVORITES;

@RestController
@RequestMapping(V1_CLIENT_FAVORITES)
public class FavoritesController extends BaseController {

    private final AddStopToFavoritesUseCase addStopToFavoritesUseCase;
    private final AddRouteToFavoritesUseCase addRouteToFavoritesUseCase;
    private final UpdateClientActivityUseCase updateClientActivityUseCase;

    public FavoritesController(AddStopToFavoritesUseCase addStopToFavoritesUseCase,
                              AddRouteToFavoritesUseCase addRouteToFavoritesUseCase,
                              UpdateClientActivityUseCase updateClientActivityUseCase,
                              MessageSource messageSource) {
        super(messageSource);
        this.addStopToFavoritesUseCase = addStopToFavoritesUseCase;
        this.addRouteToFavoritesUseCase = addRouteToFavoritesUseCase;
        this.updateClientActivityUseCase = updateClientActivityUseCase;
    }

    @Override
    protected String getControllerName() {
        return FavoritesController.class.getSimpleName();
    }


    @PostMapping("/stops/{stopId}")
    public Mono<ResponseEntity<ApiResponse<FavoriteResponse>>> toggleStopFavorite(@PathVariable String stopId) {
        return ok(getCurrentPrincipal().flatMap(principal -> {
            String clientId = principal.getClientId();

            return updateClientActivity(clientId)
                    .then(Mono.defer(() ->
                            addStopToFavoritesUseCase.execute(
                                    Mono.just(new AddStopToFavoritesUseCase.Command(clientId, stopId))
                            )
                    ))
                    .map(added -> new FavoriteResponse(
                            added,
                            added ? "Stop added to favorites" : "Stop removed from favorites"
                    ));
        }));
    }


    @PostMapping("/routes/{routeId}")
    public Mono<ResponseEntity<ApiResponse<FavoriteResponse>>> toggleRouteFavorite(@PathVariable String routeId) {
        return ok(getCurrentPrincipal().flatMap(principal -> {
            String clientId = principal.getClientId();

            return updateClientActivity(clientId)
                    .then(Mono.defer(() ->
                            addRouteToFavoritesUseCase.execute(
                                    Mono.just(new AddRouteToFavoritesUseCase.Command(clientId, routeId))
                            )
                    ))
                    .map(added -> new FavoriteResponse(
                            added,
                            added ? "Route added to favorites" : "Route removed from favorites"
                    ));
        }));
    }

    private Mono<Void> updateClientActivity(String clientId) {
        return Mono.just(new UpdateClientActivityUseCase.Command(clientId)).as(updateClientActivityUseCase::execute);
    }

    private Mono<ClientPrincipal> getCurrentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> (ClientPrincipal) auth.getPrincipal());
    }

    public record FavoriteResponse(Boolean added, String message) {}
}