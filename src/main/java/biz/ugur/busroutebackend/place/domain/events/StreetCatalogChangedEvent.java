package biz.ugur.busroutebackend.place.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;

public record StreetCatalogChangedEvent(String streetId) implements DomainEvent {
}
