package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;

public record ClientRegisteredEvent(String clientId, String phone, String platform) implements DomainEvent {

}