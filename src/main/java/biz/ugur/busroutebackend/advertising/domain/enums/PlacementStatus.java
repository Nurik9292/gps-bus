package biz.ugur.busroutebackend.advertising.domain.enums;

import lombok.Getter;

@Getter
public enum PlacementStatus {

    DRAFT("Draft — creative not yet submitted"),
    PENDING_PAYMENT("Created, awaiting payment confirmation"),
    SCHEDULED("Paid, waiting for start window"),
    ACTIVE("Currently displayed in app"),
    EXPIRED("Display window ended"),
    CANCELLED("Cancelled by admin or business");

    private final String description;

    PlacementStatus(String description) {
        this.description = description;
    }

    public boolean canTransitionTo(PlacementStatus target) {
        return switch (this) {
            case DRAFT            -> target == PENDING_PAYMENT || target == CANCELLED;
            case PENDING_PAYMENT  -> target == SCHEDULED || target == CANCELLED;
            case SCHEDULED        -> target == ACTIVE || target == CANCELLED;
            case ACTIVE           -> target == EXPIRED || target == CANCELLED;
            case EXPIRED, CANCELLED -> false;
        };
    }
}
