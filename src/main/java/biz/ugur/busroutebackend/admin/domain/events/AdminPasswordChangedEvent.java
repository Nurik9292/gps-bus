package biz.ugur.busroutebackend.admin.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminPasswordChangedEvent implements DomainEvent {

    private final String adminId;
    private final String username;
    private final Instant eventOccurredAt;

    public AdminPasswordChangedEvent(String adminId, String username) {
        this.adminId = adminId;
        this.username = username;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("AdminPasswordChanged[id=%s, username=%s]", adminId, username);
    }
}

