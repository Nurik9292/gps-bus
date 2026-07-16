package biz.ugur.busroutebackend.place.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;

public record PlaceCatalogChangedEvent(String placeId) implements DomainEvent {
}
