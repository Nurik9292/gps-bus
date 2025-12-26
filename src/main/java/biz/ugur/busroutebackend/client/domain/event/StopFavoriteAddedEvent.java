package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;

public record StopFavoriteAddedEvent(
        String favoriteId,
        String clientId,
        String stopId
) implements DomainEvent {
}
