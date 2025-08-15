package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;

public record ClientAuthenticatedEvent(String clientId, String platform) implements DomainEvent {

}