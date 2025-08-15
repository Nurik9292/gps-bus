package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;

public record ClientOtpVerifiedEvent(String clientId, String phone) implements DomainEvent {

}
