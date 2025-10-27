package biz.ugur.busroutebackend.admin.domain.events;

import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminCreatedEvent extends AdminDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String username;
    private final String fullName;
    private final Boolean isSuperAdmin;

    public AdminCreatedEvent(String adminId, String username, String fullName, Boolean isSuperAdmin) {
        super(adminId);
        this.username = username;
        this.fullName = fullName;
        this.isSuperAdmin = isSuperAdmin;
    }

    public AdminCreatedEvent(String eventId, String adminId, Instant occurredAt, int eventVersion,
                            String username, String fullName, Boolean isSuperAdmin) {
        super(eventId, adminId, occurredAt, eventVersion);
        this.username = username;
        this.fullName = fullName;
        this.isSuperAdmin = isSuperAdmin;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String toString() {
        return String.format("AdminCreatedEvent[id=%s, username=%s, fullName=%s, superAdmin=%s, version=%d]",
                getAdminId(), username, fullName, isSuperAdmin, getEventVersion());
    }
}