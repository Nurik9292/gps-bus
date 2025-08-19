package biz.ugur.busroutebackend.admin.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminProfileUpdatedEvent implements DomainEvent {

    private final String adminId;
    private final String username;
    private final String fullName;
    private final String avatar;
    private final boolean avatarChanged;
    private final Instant eventOccurredAt;

    public AdminProfileUpdatedEvent(String adminId, String username, String fullName, String avatar, boolean avatarChanged) {
        this.adminId = adminId;
        this.username = username;
        this.fullName = fullName;
        this.avatar = avatar;
        this.avatarChanged = avatarChanged;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("AdminProfileUpdated[id=%s, username=%s, fullName=%s, avatarChanged=%s]",
                adminId, username, fullName, avatarChanged);
    }
}