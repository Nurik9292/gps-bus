package biz.ugur.busroutebackend.routing.domain.valueobjects;

import biz.ugur.busroutebackend.routing.domain.enums.SegmentType;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@EqualsAndHashCode(callSuper = false)
public class TripOption extends ValueObject {

    private final String optionId;
    private final TripType tripType;
    private final List<RouteSegment> routeSegments;
    private final int totalTravelMinutes;
    private final int totalWalkingMinutes;
    private final int totalBusRideMinutes;
    private final int totalWaitingMinutes;
    private final int initialWaitingMinutes;
    private final int transfersCount;
    private final double estimatedCostManat;
    private final LocalDateTime estimatedDeparture;
    private final LocalDateTime estimatedArrival;
    private final double comfortScore;


    public TripOption(TripType tripType, List<RouteSegment> routeSegments,
                      int initialWaitingMinutes, LocalDateTime departureTime) {
        this.optionId = UUID.randomUUID().toString();
        this.tripType = validateTripType(tripType);
        this.routeSegments = validateAndCopySegments(routeSegments);
        this.initialWaitingMinutes = validateInitialWaitingTime(initialWaitingMinutes);

        this.totalWalkingMinutes = calculateTotalWalkingTime();
        this.totalBusRideMinutes = calculateTotalBusRideTime();
        this.totalWaitingMinutes = calculateTotalWaitingTime();
        this.totalTravelMinutes = totalWalkingMinutes + totalBusRideMinutes + totalWaitingMinutes + this.initialWaitingMinutes;
        this.transfersCount = calculateTransfersCount();
        this.estimatedCostManat = calculateEstimatedCost();
        this.estimatedDeparture = calculateDeparture(departureTime);
        this.estimatedArrival = calculateArrival();
        this.comfortScore = calculateComfortScore();

        validateTripLogic();
    }

    private int validateInitialWaitingTime(int waitingMinutes) {
        if (waitingMinutes < 0) {
            throw new IllegalArgumentException("Initial waiting time cannot be negative");
        }
        return Math.min(waitingMinutes, 60);
    }

    public boolean isFasterThan(TripOption other) {
        return this.totalTravelMinutes < other.totalTravelMinutes;
    }

    public boolean hasFewerTransfersThan(TripOption other) {
        return this.transfersCount < other.transfersCount;
    }

    public boolean isCheaperThan(TripOption other) {
        return this.estimatedCostManat < other.estimatedCostManat;
    }

    public boolean isMoreComfortableThan(TripOption other) {
        return this.comfortScore > other.comfortScore;
    }

    public String getSummary() {
        if (tripType == TripType.DIRECT) {
            String routeNumbers = getUsedRouteNumbers();
            return String.format("Прямой маршрут %s - %d мин", routeNumbers, totalTravelMinutes);
        } else {
            return String.format("%d пересадк%s - %d мин",
                    transfersCount,
                    getTransferSuffix(transfersCount),
                    totalTravelMinutes);
        }
    }

    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Поездка займет %d мин", totalTravelMinutes));

        if (totalWalkingMinutes > 0) {
            sb.append(String.format(" (пешком %d мин)", totalWalkingMinutes));
        }

        if (initialWaitingMinutes > 0) {
            sb.append(String.format(", ожидание %d мин", initialWaitingMinutes));
        }

        if (transfersCount > 0) {
            sb.append(String.format(", %d пересадк%s", transfersCount, getTransferSuffix(transfersCount)));
        }

        sb.append(String.format(", стоимость %.1f маната", estimatedCostManat));

        return sb.toString();
    }

    public List<String> getUsedRoutes() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .map(RouteSegment::getRouteNumber)
                .distinct()
                .collect(Collectors.toList());
    }

    public String getUsedRouteNumbers() {
        List<String> routes = getUsedRoutes();
        if (routes.isEmpty()) return "пешком";
        return String.join(", ", routes);
    }

    public boolean isValidForTrip(Coordinates origin, Coordinates destination) {
        if (routeSegments.isEmpty()) return false;

        RouteSegment firstSegment = routeSegments.getFirst();
        RouteSegment lastSegment = routeSegments.getLast();

        DistanceCalculationService distanceService = new DistanceCalculationService();

        double startDistance = distanceService.calculateDistance(
            firstSegment.getFromLocation().getLatitudeAsDouble(),
            firstSegment.getFromLocation().getLongitudeAsDouble(),
            origin.getLatitudeAsDouble(),
            origin.getLongitudeAsDouble()
        ).getMeters();

        if (startDistance > 1000) {
            return false;
        }

        double endDistance = distanceService.calculateDistance(
            lastSegment.getToLocation().getLatitudeAsDouble(),
            lastSegment.getToLocation().getLongitudeAsDouble(),
            destination.getLatitudeAsDouble(),
            destination.getLongitudeAsDouble()
        ).getMeters();

        if (endDistance > 1000) {
            return false;
        }

        return true;
    }

    public double getQualityScore() {
        double speedScore = Math.max(0, 100 - totalTravelMinutes);
        double transferScore = Math.max(0, 100 - transfersCount * 25);
        double walkingScore = Math.max(0, 100 - totalWalkingMinutes * 3);


        return (speedScore * 0.4 + transferScore * 0.3 + walkingScore * 0.2 + comfortScore * 0.1);
    }

    public boolean isAccessible() {

        boolean walkingOk = routeSegments.stream()
                .filter(s -> s.getType() == SegmentType.WALKING)
                .allMatch(s -> s.getDurationMinutes() <= 5);


        boolean transfersOk = transfersCount <= 1;

        return walkingOk && transfersOk;
    }

    public double getReliabilityScore() {
        double baseReliability = 0.95;


        double transferPenalty = transfersCount * 0.05;


        double walkingPenalty = Math.max(0, (totalWalkingMinutes - 10) * 0.01);

        return Math.max(0.5, baseReliability - transferPenalty - walkingPenalty);
    }



    private TripType validateTripType(TripType tripType) {
        if (tripType == null) {
            throw new IllegalArgumentException("Trip type cannot be null");
        }
        return tripType;
    }

    private List<RouteSegment> validateAndCopySegments(List<RouteSegment> segments) {
        if (segments == null || segments.isEmpty()) {
            throw new IllegalArgumentException("Route segments cannot be null or empty");
        }

        DistanceCalculationService distanceService = new DistanceCalculationService();

        for (int i = 1; i < segments.size(); i++) {
            Coordinates prevEnd = segments.get(i - 1).getToLocation();
            Coordinates currentStart = segments.get(i).getFromLocation();

            double distance = distanceService.calculateDistance(
                prevEnd.getLatitudeAsDouble(),
                prevEnd.getLongitudeAsDouble(),
                currentStart.getLatitudeAsDouble(),
                currentStart.getLongitudeAsDouble()
            ).getMeters();

            if (distance > 100) {
                throw new IllegalArgumentException("Route segments are not connected");
            }
        }

        return new ArrayList<>(segments);
    }

    private void validateTripLogic() {

        if (!routeSegments.isEmpty()) {
            SegmentType firstType = routeSegments.getFirst().getType();
            if (firstType == SegmentType.TRANSFER) {
                throw new IllegalArgumentException("Trip cannot start with a transfer");
            }
        }


        for (int i = 0; i < routeSegments.size() - 1; i++) {
            if (routeSegments.get(i).getType() == SegmentType.TRANSFER) {
                SegmentType nextType = routeSegments.get(i + 1).getType();
                if (nextType != SegmentType.BUS_RIDE) {
                    throw new IllegalArgumentException("Transfer must be followed by bus ride");
                }
            }
        }
    }

    private int calculateTotalWalkingTime() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.WALKING)
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTotalBusRideTime() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTotalWaitingTime() {
        return routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.TRANSFER)
                .mapToInt(RouteSegment::getDurationMinutes)
                .sum();
    }

    private int calculateTransfersCount() {
        return (int) routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.TRANSFER)
                .count();
    }

    private double calculateEstimatedCost() {
        long busRides = routeSegments.stream()
                .filter(segment -> segment.getType() == SegmentType.BUS_RIDE)
                .map(RouteSegment::getRouteNumber)
                .distinct()
                .count();

        return busRides * 1.0;
    }

    private LocalDateTime calculateDeparture(LocalDateTime baseTime) {
        int walkingToFirstStop = getWalkingTimeToFirstStop();
        return baseTime.plusMinutes(walkingToFirstStop + initialWaitingMinutes);
    }

    private int getWalkingTimeToFirstStop() {
        if (routeSegments.isEmpty()) {
            return 0;
        }
        RouteSegment firstSegment = routeSegments.getFirst();
        if (firstSegment.getType() == SegmentType.WALKING) {
            return firstSegment.getDurationMinutes();
        }
        return 0;
    }

    private LocalDateTime calculateArrival() {
        return estimatedDeparture.plusMinutes(totalTravelMinutes);
    }

    private double calculateComfortScore() {
        double baseScore = 100.0;


        baseScore -= transfersCount * 15;
        baseScore -= Math.max(0, totalWalkingMinutes - 5) * 2;
        baseScore -= Math.max(0, totalTravelMinutes - 30) * 0.5;

        return Math.max(0, Math.min(100, baseScore));
    }

    private String getTransferSuffix(int count) {
        if (count == 1) return "а";
        if (count >= 2 && count <= 4) return "и";
        return "";
    }

    @Override
    public String toString() {
        return String.format("TripOption[%s, %d min, %d transfers, %.1f manat]",
                tripType, totalTravelMinutes, transfersCount, estimatedCostManat);
    }
}