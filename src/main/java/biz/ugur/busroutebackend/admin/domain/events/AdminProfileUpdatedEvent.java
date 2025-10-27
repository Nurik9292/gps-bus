package biz.ugur.busroutebackend.admin.domain.events;

import lombok.Getter;

import java.time.Instant;

@Getter
public class AdminProfileUpdatedEvent extends AdminDomainEvent {

    public static final int CURRENT_VERSION = 1;

    private final String username;
    private final String fullName;
    private final String avatar;
    private final boolean avatarChanged;

    public AdminProfileUpdatedEvent(String adminId, String username, String fullName, String avatar, boolean avatarChanged) {
        super(adminId);
        this.username = username;
        this.fullName = fullName;
        this.avatar = avatar;
        this.avatarChanged = avatarChanged;
    }

    public AdminProfileUpdatedEvent(String eventId, String adminId, Instant occurredAt, int eventVersion,
                                   String username, String fullName, String avatar, boolean avatarChanged) {
        super(eventId, adminId, occurredAt, eventVersion);
        this.username = username;
        this.fullName = fullName;
        this.avatar = avatar;
        this.avatarChanged = avatarChanged;
    }

    @Override
    protected int getCurrentVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public String toString() {
        return String.format("AdminProfileUpdatedEvent[id=%s, username=%s, fullName=%s, avatarChanged=%s, version=%d]",
                getAdminId(), username, fullName, avatarChanged, getEventVersion());
    }
}