package biz.ugur.busroutebackend.routing.domain.volumeojects;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = false)
public class TripSearchCriteria extends ValueObject {

    private final int maxWalkingDistanceMeters;
    private final int maxTransfers;
    private final boolean prioritizeSpeed;
    private final boolean prioritizeFewerTransfers;

    public TripSearchCriteria(int maxWalkingDistanceMeters, int maxTransfers,
                              boolean prioritizeSpeed, boolean prioritizeFewerTransfers) {
        this.maxWalkingDistanceMeters = maxWalkingDistanceMeters;
        this.maxTransfers = maxTransfers;
        this.prioritizeSpeed = prioritizeSpeed;
        this.prioritizeFewerTransfers = prioritizeFewerTransfers;
    }

    public static TripSearchCriteria defaultCriteria() {
        return new TripSearchCriteria(800, 2, true, true);
    }

    public static TripSearchCriteria fastestRoute() {
        return new TripSearchCriteria(1000, 3, true, false);
    }

    public static TripSearchCriteria fewestTransfers() {
        return new TripSearchCriteria(600, 1, false, true);
    }

    @Override
    public String toString() {
        return String.format("TripCriteria[walk≤%dm, transfers≤%d, speed=%s, transfers=%s]",
                maxWalkingDistanceMeters, maxTransfers, prioritizeSpeed, prioritizeFewerTransfers);
    }
}