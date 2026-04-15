package biz.ugur.busroutebackend.advertising.domain.exceptions;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;

public class PlacementStateTransitionException extends AdvertisingDomainException {

    public PlacementStateTransitionException(PlacementStatus from, PlacementStatus to) {
        super("ILLEGAL_STATE_TRANSITION",
                String.format("Illegal placement status transition: %s → %s", from, to),
                Severity.WARNING);
    }
}
