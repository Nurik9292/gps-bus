package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;

public record StopFavoriteRemovedEvent(
        String clientId,
        String stopId
) implements DomainEvent {
}
