package biz.ugur.busroutebackend.client.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;


public record ClientCreatedViaServiceEvent(
        String clientId,
        String serviceId,
        String externalUserId
) implements DomainEvent {

}
