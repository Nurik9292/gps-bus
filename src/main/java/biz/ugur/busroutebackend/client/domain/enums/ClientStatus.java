package biz.ugur.busroutebackend.client.domain.enums;

import lombok.Getter;

@Getter
public enum ClientStatus {
    INACTIVE("Inactive"),
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    BLOCKED("Blocked");

    private final String displayName;

    ClientStatus(String displayName) {
        this.displayName = displayName;
    }

    public boolean canLogin() {
        return this == ACTIVE;
    }
}