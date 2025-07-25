package biz.ugur.busroutebackend.routing.domain.model;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.events.TripOptionsCalculatedEvent;
import biz.ugur.busroutebackend.routing.domain.events.TripPlanCreatedEvent;
import biz.ugur.busroutebackend.routing.domain.volumeojects.Location;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripSearchCriteria;
import biz.ugur.busroutebackend.shared.domain.AggregateRoot;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

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

    private final Location originLocation;
    private final Location destinationLocation;
    private final List<TripOption> tripOptions;
    private final LocalDateTime searchTime;
    private final TripSearchCriteria searchCriteria;

    public TripPlan(TripPlanId tripPlanId, Location originLocation, Location destinationLocation) {
        this.tripPlanId = tripPlanId != null ? tripPlanId : TripPlanId.generate();
        this.originLocation = validateLocation(originLocation, "Origin");
        this.destinationLocation = validateLocation(destinationLocation, "Destination");
        this.tripOptions = new ArrayList<>();
        this.searchTime = LocalDateTime.now();
        this.searchCriteria = TripSearchCriteria.defaultCriteria();

        registerEvent(new TripPlanCreatedEvent(
                this.tripPlanId.getValue(),
                originLocation.getLatitude(),
                originLocation.getLongitude(),
                destinationLocation.getLatitude(),
                destinationLocation.getLongitude()
        ));
    }

    public static TripPlan searchTrips(Location fromLocation, Location toLocation, TripSearchCriteria criteria) {
        TripPlan plan = new TripPlan(TripPlanId.generate(), fromLocation, toLocation);
        // Business logic will be in Use Cases
        return plan;
    }

    public void addTripOption(TripOption option) {
        if (option == null) {
            throw new IllegalArgumentException("Trip option cannot be null");
        }

        // Validate that this option is for our trip
        if (!option.isValidForTrip(originLocation, destinationLocation)) {
            throw new IllegalArgumentException("Trip option is not valid for this trip plan");
        }

        tripOptions.add(option);

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

    public boolean hasViableOptions() {
        return !tripOptions.isEmpty();
    }

    public List<TripOption> getDirectOptions() {
        return tripOptions.stream()
                .filter(option -> option.getTripType() == TripType.DIRECT)
                .collect(Collectors.toList());
    }

    public List<TripOption> getTransferOptions() {
        return tripOptions.stream()
                .filter(option -> option.getTripType() != TripType.DIRECT)
                .collect(Collectors.toList());
    }

    @Override
    public TripPlanId getId() {
        return tripPlanId;
    }

    public List<TripOption> getTripOptions() {
        return new ArrayList<>(tripOptions);
    }

    // Private helper methods
    private Location validateLocation(Location location, String type) {
        if (location == null) {
            throw new IllegalArgumentException(type + " location cannot be null");
        }
        location.validateTurkmenistanBounds();
        return location;
    }

    private int compareOptions(TripOption a, TripOption b) {
        // Sort by: 1) transfers count, 2) total time, 3) walking time
        int transfersComparison = Integer.compare(a.getTransfersCount(), b.getTransfersCount());
        if (transfersComparison != 0) return transfersComparison;

        int timeComparison = Integer.compare(a.getTotalTravelMinutes(), b.getTotalTravelMinutes());
        if (timeComparison != 0) return timeComparison;

        return Integer.compare(a.getTotalWalkingMinutes(), b.getTotalWalkingMinutes());
    }
}