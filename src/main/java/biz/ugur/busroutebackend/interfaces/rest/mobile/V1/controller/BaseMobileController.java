package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.controller;

import biz.ugur.busroutebackend.client.application.usecase.RouteIsFavoriteUseCase;
import biz.ugur.busroutebackend.client.infrastructure.security.ClientPrincipal;
import biz.ugur.busroutebackend.shared.infrastructure.web.BasePaginatedController;
import org.springframework.context.MessageSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.reactive.accept.RequestedContentTypeResolver;
import reactor.core.publisher.Mono;

import java.util.Optional;

public abstract class BaseMobileController extends BasePaginatedController {

    private final RequestedContentTypeResolver requestedContentTypeResolver;

    protected final RouteIsFavoriteUseCase routeIsFavoriteUseCase;



    protected BaseMobileController(MessageSource messageSource,
                                   RequestedContentTypeResolver requestedContentTypeResolver,
                                   RouteIsFavoriteUseCase routeIsFavoriteUseCase) {
        super(messageSource);
        this.requestedContentTypeResolver = requestedContentTypeResolver;
        this.routeIsFavoriteUseCase = routeIsFavoriteUseCase;
    }

    protected RequestedContentTypeResolver getRequestedContentTypeResolver() {
        return requestedContentTypeResolver;
    }


    protected Mono<ClientPrincipal> getCurrentPrincipal() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getPrincipal)
                .filter(principal -> principal instanceof ClientPrincipal)
                .cast(ClientPrincipal.class);
    }


    protected Mono<Optional<String>> getOptionalClientId() {
        return getCurrentPrincipal()
                .map(principal -> Optional.of(principal.getClientId()))
                .defaultIfEmpty(Optional.empty());
    }
}
