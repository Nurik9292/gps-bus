package biz.ugur.busroutebackend.admin.domain.events;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminCreatedEvent implements DomainEvent {

    private final String adminId;
    private final String username;
    private final String fullName;
    private final Boolean isSuperAdmin;
    private final Instant eventOccurredAt;

    public AdminCreatedEvent(String adminId, String username, String fullName, Boolean isSuperAdmin) {
        this.adminId = adminId;
        this.username = username;
        this.fullName = fullName;
        this.isSuperAdmin = isSuperAdmin;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("AdminCreated[id=%s, username=%s, fullName=%s, superAdmin=%s]",
                adminId, username, fullName, isSuperAdmin);
    }
}