package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;

public record RouteFavoriteAddedEvent(
        String favoriteId,
        String clientId,
        String routeId
) implements DomainEvent {
}
