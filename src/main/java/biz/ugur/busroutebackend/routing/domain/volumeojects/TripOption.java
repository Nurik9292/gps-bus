package biz.ugur.busroutebackend.routing.domain.volumeojects;

import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class TripOption extends ValueObject {

    private final String optionId;
    private final TripType tripType;
    private final List<RouteSegment> routeSegments;
    private final int totalTravelMinutes;
    private final int totalWalkingMinutes;
    private final int transfersCount;
    private final LocalDateTime estimatedDeparture;
    private final LocalDateTime estimatedArrival;

    public TripOption(TripType tripType, List<RouteSegment> routeSegments) {
        this.optionId = UUID.randomUUID().toString();
        this.tripType = tripType;
        this.routeSegments = new ArrayList<>(routeSegments);
        this.totalTravelMinutes = calculateTotalTravelTime();
        this.totalWalkingMinutes = calculateTotalWalkingTime();
        this.transfersCount = calculateTransfersCount();
        this.estimatedDeparture = calculateDeparture();
        this.estimatedArrival = calculateArrival();
    }

    public boolean isFasterThan(TripOption other) {
        return this.totalTravelMinutes < other.totalTravelMinutes;
    }

    public boolean hasFewerTransfersThan(TripOption other) {
        return this.transfersCount < other.transfersCount;
    }

    public String getSummary() {
        if (tripType == TripType.DIRECT) {
            return String.format("Direct route - %d min total", totalTravelMinutes);
        } else {
            return String.format("%d transfer(s) - %d min total", transfersCount, totalTravelMinutes);
        }
    }

    public boolean isValidForTrip(Location origin, Location destination) {
        if (routeSegments.isEmpty()) return false;

        RouteSegment firstSegment = routeSegments.getFirst();
        RouteSegment lastSegment = routeSegments.getLast();

        // Check if first segment starts near origin and last segment ends near destination
        double startDistance = firstSegment.getFromLocation().distanceTo(origin);
        double endDistance = lastSegment.getToLocation().distanceTo(destination);

        return startDistance <= 1000 && endDistance <= 1000; // Within 1km
    }

    private int calculateTotalTravelTime() {
        return routeSegments.stream()
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTotalWalkingTime() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.WALKING)
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTransfersCount() {
        return (int) routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.TRANSFER)
                .count();
    }

    private LocalDateTime calculateDeparture() {
        return LocalDateTime.now().plusMinutes(5); // Assume 5 min to reach first stop
    }

    private LocalDateTime calculateArrival() {
        return estimatedDeparture.plusMinutes(totalTravelMinutes);
    }

    @Override
    public String toString() {
        return String.format("TripOption[%s, %d min, %d transfers]",
                tripType, totalTravelMinutes, transfersCount);
    }
}
