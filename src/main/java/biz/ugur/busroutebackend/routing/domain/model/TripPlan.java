package biz.ugur.busroutebackend.routing.domain.model;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.events.TripOptionsCalculatedEvent;
import biz.ugur.busroutebackend.routing.domain.events.TripPlanCreatedEvent;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.geospatial.domain.constants.TurkmenistanBounds;
import biz.ugur.busroutebackend.geospatial.domain.services.DistanceCalculationService;
import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@Getter
@Table("trip_plans")
public class TripPlan extends AggregateRoot<TripPlan, TripPlanId> {

    @Id
    private TripPlanId tripPlanId;

    private final Coordinates originLocation;
    private final Coordinates destinationLocation;
    private final DistanceCalculationService distanceService;
    private final List<TripOption> tripOptions;
    private final LocalDateTime searchTime;
    private final TripSearchCriteria searchCriteria;

    @Column("origin_latitude")
    private final Double originLatitude;

    @Column("origin_longitude")
    private final Double originLongitude;

    @Column("destination_latitude")
    private final Double destinationLatitude;

    @Column("destination_longitude")
    private final Double destinationLongitude;

    @Column("search_time")
    private final LocalDateTime searchTimeDb;

    @Column("options_count")
    private final Integer optionsCount;

    @Column("max_transfers")
    private final Integer maxTransfers;

    @Column("max_walking_distance_meters")
    private final Integer maxWalkingDistanceMeters;


    public TripPlan(TripPlanId tripPlanId,
                    Coordinates originLocation,
                    Coordinates destinationLocation,
                    TripSearchCriteria searchCriteria,
                    DistanceCalculationService distanceService) {
        this.tripPlanId = tripPlanId != null ? tripPlanId : TripPlanId.generate();
        this.originLocation = validateCoordinates(originLocation, "Origin");
        this.destinationLocation = validateCoordinates(destinationLocation, "Destination");
        this.distanceService = distanceService != null ? distanceService : new DistanceCalculationService();
        this.tripOptions = new ArrayList<>();
        this.searchTime = LocalDateTime.now();
        this.searchCriteria = searchCriteria != null ? searchCriteria : TripSearchCriteria.defaultCriteria();

        this.originLatitude = originLocation.getLatitudeAsDouble();
        this.originLongitude = originLocation.getLongitudeAsDouble();
        this.destinationLatitude = destinationLocation.getLatitudeAsDouble();
        this.destinationLongitude = destinationLocation.getLongitudeAsDouble();
        this.searchTimeDb = this.searchTime;
        this.optionsCount = 0;
        this.maxTransfers = this.searchCriteria.getMaxTransfers();
        this.maxWalkingDistanceMeters = this.searchCriteria.getMaxWalkingDistanceMeters();

        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.version = 0L;

        double distance = this.distanceService.calculateDistance(
            originLocation.getLatitudeAsDouble(), originLocation.getLongitudeAsDouble(),
            destinationLocation.getLatitudeAsDouble(), destinationLocation.getLongitudeAsDouble()
        ).getMeters();

        if (distance < 100) {
            throw new IllegalArgumentException("Origin and destination are too close. Minimum distance: 100m");
        }

        registerEvent(new TripPlanCreatedEvent(
                this.tripPlanId.getValue(),
                originLocation.getLatitudeAsDouble(),
                originLocation.getLongitudeAsDouble(),
                destinationLocation.getLatitudeAsDouble(),
                destinationLocation.getLongitudeAsDouble()
        ));
    }

    public TripPlan(TripPlanId tripPlanId,
                    Double originLatitude,
                    Double originLongitude,
                    Double destinationLatitude,
                    Double destinationLongitude,
                    LocalDateTime searchTime,
                    Integer optionsCount,
                    Integer maxTransfers,
                    Integer maxWalkingDistanceMeters) {
        this.tripPlanId = tripPlanId;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.searchTimeDb = searchTime;
        this.optionsCount = optionsCount;
        this.maxTransfers = maxTransfers;
        this.maxWalkingDistanceMeters = maxWalkingDistanceMeters;

        this.originLocation = Coordinates.of(originLatitude, originLongitude);
        this.destinationLocation = Coordinates.of(destinationLatitude, destinationLongitude);
        this.distanceService = new DistanceCalculationService();
        this.searchTime = searchTime;
        this.searchCriteria = new TripSearchCriteria(
                maxWalkingDistanceMeters != null ? maxWalkingDistanceMeters : 1200,
                maxTransfers != null ? maxTransfers : 2,
                true, true
        );
        this.tripOptions = new ArrayList<>();
    }

    public TripPlan(Coordinates originLocation, Coordinates destinationLocation, DistanceCalculationService distanceService) {
        this(TripPlanId.generate(), originLocation, destinationLocation, TripSearchCriteria.defaultCriteria(), distanceService);
    }

    public void addTripOption(TripOption option) {
        if (option == null) {
            throw new IllegalArgumentException("Trip option cannot be null");
        }

        if (!option.isValidForTrip(originLocation, destinationLocation)) {
            System.err.println("Trip option validation failed for option " + option.getOptionId() +
                    ": Option start/end too far from trip origin/destination");
            return;
        }

        if (!isOptionAcceptable(option)) {
            System.err.println("Trip option " + option.getOptionId() + " rejected by isOptionAcceptable: " +
                    "transfers=" + option.getTransfersCount() + " (max " + searchCriteria.getMaxTransfers() + "), " +
                    "walkingMinutes=" + option.getTotalWalkingMinutes() + " (max " + (searchCriteria.getMaxWalkingDistanceMeters() / 66.67) + "), " +
                    "totalMinutes=" + option.getTotalTravelMinutes() + " (max 240)");
            return;
        }

        if (tripOptions.size() >= 10) {
            TripOption worstOption = findWorstOption();
            if (worstOption != null && isOptionBetter(option, worstOption)) {
                tripOptions.remove(worstOption);
                tripOptions.add(option);
            }
        } else {
            tripOptions.add(option);
            System.err.println("Trip option " + option.getOptionId() + " successfully added to TripPlan. Total options: " + tripOptions.size());
        }

        registerEvent(new TripOptionsCalculatedEvent(
                this.tripPlanId.getValue(),
                tripOptions.size(),
                option.getTripType().name(),
                option.getTotalTravelMinutes()
        ));
    }


    public List<TripOption> getBestOptions(int maxCount) {
        return tripOptions.stream()
                .sorted(this::compareOptions)
                .limit(maxCount)
                .collect(Collectors.toList());
    }


    public TripOption getFastestOption() {
        return tripOptions.stream()
                .min(Comparator.comparing(TripOption::getTotalTravelMinutes))
                .orElse(null);
    }

    public TripOption getOptionWithFewestTransfers() {
        return tripOptions.stream()
                .min(Comparator.comparing(TripOption::getTransfersCount)
                        .thenComparing(TripOption::getTotalTravelMinutes))
                .orElse(null);
    }

    public TripOption getCheapestOption() {
        return tripOptions.stream()
                .min(Comparator.comparing(option -> (option.getTransfersCount() + 1) * 1.0)) // 1 манат за поездку
                .orElse(null);
    }

    public boolean hasViableOptions() {
        return !tripOptions.isEmpty();
    }

    public List<TripOption> getDirectOptions() {
        return tripOptions.stream()
                .filter(option -> option.getTripType() == TripType.DIRECT)
                .sorted(Comparator.comparing(TripOption::getTotalTravelMinutes))
                .collect(Collectors.toList());
    }

    public List<TripOption> getTransferOptions() {
        return tripOptions.stream()
                .filter(option -> option.getTransfersCount() > 0)
                .collect(Collectors.toList());
    }


    public boolean isWalkable() {
        double distanceMeters = distanceService.calculateDistance(
            originLocation.getLatitudeAsDouble(), originLocation.getLongitudeAsDouble(),
            destinationLocation.getLatitudeAsDouble(), destinationLocation.getLongitudeAsDouble()
        ).getMeters();
        return distanceMeters <= searchCriteria.getMaxWalkingDistanceMeters();
    }

    public int getWalkingTimeMinutes() {
        if (!isWalkable()) return -1;

        double distanceMeters = distanceService.calculateDistance(
            originLocation.getLatitudeAsDouble(), originLocation.getLongitudeAsDouble(),
            destinationLocation.getLatitudeAsDouble(), destinationLocation.getLongitudeAsDouble()
        ).getMeters();
        return (int) Math.ceil(distanceMeters / 83.33);
    }

    @Override
    public TripPlanId getId() {
        return tripPlanId;
    }

    public List<TripOption> getTripOptions() {
        return new ArrayList<>(tripOptions);
    }

    private Coordinates validateCoordinates(Coordinates coordinates, String type) {
        if (coordinates == null) {
            throw new IllegalArgumentException(type + " coordinates cannot be null");
        }
        if (!TurkmenistanBounds.isWithinStandardBounds(
            coordinates.getLatitudeAsDouble(),
            coordinates.getLongitudeAsDouble())) {
            throw new IllegalArgumentException(
                type + " coordinates are outside Turkmenistan bounds: " + coordinates
            );
        }
        return coordinates;
    }

    private boolean isOptionAcceptable(TripOption option) {
        if (option.getTransfersCount() > searchCriteria.getMaxTransfers()) {
            return false;
        }

        double maxWalkingMinutes = searchCriteria.getMaxWalkingDistanceMeters() / 66.67;
        if (option.getTotalWalkingMinutes() > maxWalkingMinutes) {
            return false;
        }

        if (option.getTotalTravelMinutes() > 240) {
            return false;
        }

        return true;
    }

    private TripOption findWorstOption() {
        return tripOptions.stream()
                .max(this::compareOptions)
                .orElse(null);
    }

    private boolean isOptionBetter(TripOption option1, TripOption option2) {
        return compareOptions(option1, option2) < 0;
    }

    private int compareOptions(TripOption a, TripOption b) {
        boolean test = searchCriteria.isPrioritizeFewerTransfers();
        if (searchCriteria.isPrioritizeFewerTransfers()) {
            int transfersComparison = Integer.compare(a.getTransfersCount(), b.getTransfersCount());
            if (transfersComparison != 0) return transfersComparison;
        }

        if (searchCriteria.isPrioritizeSpeed()) {
            int timeComparison = Integer.compare(a.getTotalTravelMinutes(), b.getTotalTravelMinutes());
            if (timeComparison != 0) return timeComparison;
        }

        if (!searchCriteria.isPrioritizeFewerTransfers()) {
            int transfersComparison = Integer.compare(a.getTransfersCount(), b.getTransfersCount());
            if (transfersComparison != 0) return transfersComparison;
        }

        if (!searchCriteria.isPrioritizeSpeed()) {
            int timeComparison = Integer.compare(a.getTotalTravelMinutes(), b.getTotalTravelMinutes());
            if (timeComparison != 0) return timeComparison;
        }

        return Integer.compare(a.getTotalWalkingMinutes(), b.getTotalWalkingMinutes());
    }

    public static TripPlan empty(Coordinates fromLocation, Coordinates toLocation, TripSearchCriteria searchCriteria, DistanceCalculationService distanceService) {
        return new TripPlan(
                TripPlanId.generate(),
                fromLocation,
                toLocation,
                searchCriteria,
                distanceService
        );
    }

    public record TripPlanStatistics(
            int directOptionsCount,
            int transferOptionsCount,
            int fastestTimeMinutes,
            int averageTimeMinutes,
            double averageCostManat
    ) {}
}