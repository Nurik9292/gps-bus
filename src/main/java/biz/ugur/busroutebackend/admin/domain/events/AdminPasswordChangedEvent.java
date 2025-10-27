package biz.ugur.busroutebackend.admin.domain.events;

import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminPasswordChangedEvent extends AdminDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String username;

    public AdminPasswordChangedEvent(String adminId, String username) {
        super(adminId);
        this.username = username;
    }

    public AdminPasswordChangedEvent(String eventId, String adminId, Instant occurredAt, int eventVersion,
                                    String username) {
        super(eventId, adminId, occurredAt, eventVersion);
        this.username = username;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String toString() {
        return String.format("AdminPasswordChangedEvent[id=%s, username=%s, version=%d]",
                getAdminId(), username, getEventVersion());
    }
}

