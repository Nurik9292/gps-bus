package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;

public record RouteFavoriteRemovedEvent(
        String clientId,
        String routeId
) implements DomainEvent {
}
