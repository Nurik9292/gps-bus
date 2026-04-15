package biz.ugur.busroutebackend.business.domain.enums;

import lombok.Getter;

/**
 * Lifecycle of a business onboarding record.
 *
 * <p>State transitions (driven by admin actions):
 * <pre>
 *                 ┌── REJECTED  (terminal, re-application requires a new entity)
 *   PENDING ─────┤
 *                 └── APPROVED ──┬── SUSPENDED (can be re-activated → APPROVED)
 *                                │
 *                                └── (stays APPROVED otherwise)
 * </pre>
 */
@Getter
public enum BusinessStatus {

    PENDING("Awaiting admin review"),
    APPROVED("Approved — can buy ad placements"),
    REJECTED("Rejected by admin — terminal state"),
    SUSPENDED("Temporarily disabled by admin");

    private final String description;

    BusinessStatus(String description) {
        this.description = description;
    }

    public boolean canTransitionTo(BusinessStatus target) {
        return switch (this) {
            case PENDING   -> target == APPROVED || target == REJECTED;
            case APPROVED  -> target == SUSPENDED;
            case SUSPENDED -> target == APPROVED;
            case REJECTED  -> false;
        };
    }

    public boolean isActive() {
        return this == APPROVED;
    }
}
