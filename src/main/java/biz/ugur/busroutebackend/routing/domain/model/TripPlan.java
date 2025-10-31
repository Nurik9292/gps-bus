package biz.ugur.busroutebackend.routing.domain.model;

import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.events.TripOptionsCalculatedEvent;
import biz.ugur.busroutebackend.routing.domain.events.TripPlanCreatedEvent;
import biz.ugur.busroutebackend.routing.domain.service.TripOptionComparator;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


@ToString
@EqualsAndHashCode(callSuper = false)
@Getter
public class TripPlan extends AggregateRoot<TripPlan, TripPlanId> {

    private static final int MAX_OPTIONS_PER_PLAN = 10;

    private final TripPlanId id;
    private final Coordinates originLocation;
    private final Coordinates destinationLocation;
    private final TripSearchCriteria searchCriteria;
    private final LocalDateTime searchTime;
    private final List<TripOption> tripOptions;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;


    @Builder
    private TripPlan(
            TripPlanId id,
            Coordinates originLocation,
            Coordinates destinationLocation,
            TripSearchCriteria searchCriteria,
            LocalDateTime searchTime,
            List<TripOption> tripOptions,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        this.id = id;
        this.originLocation = originLocation;
        this.destinationLocation = destinationLocation;
        this.searchCriteria = searchCriteria;
        this.searchTime = searchTime != null ? searchTime : LocalDateTime.now();
        this.tripOptions = tripOptions != null ? new ArrayList<>(tripOptions) : new ArrayList<>();
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.updatedAt = updatedAt != null ? updatedAt : LocalDateTime.now();
        this.version = version != null ? version : 0L;
    }

    public static TripPlan create(
            Coordinates originLocation,
            Coordinates destinationLocation,
            TripSearchCriteria searchCriteria) {

        TripPlan plan = TripPlan.builder()
                .id(TripPlanId.generate())
                .originLocation(originLocation)
                .destinationLocation(destinationLocation)
                .searchCriteria(searchCriteria != null ? searchCriteria : TripSearchCriteria.defaultCriteria())
                .searchTime(LocalDateTime.now())
                .tripOptions(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();

        plan.registerEvent(new TripPlanCreatedEvent(
                plan.id.getValue(),
                originLocation.getLatitudeAsDouble(),
                originLocation.getLongitudeAsDouble(),
                destinationLocation.getLatitudeAsDouble(),
                destinationLocation.getLongitudeAsDouble()
        ));

        return plan;
    }


    public static TripPlan createWithId(
            TripPlanId id,
            Coordinates originLocation,
            Coordinates destinationLocation,
            TripSearchCriteria searchCriteria) {

        TripPlan plan = TripPlan.builder()
                .id(id)
                .originLocation(originLocation)
                .destinationLocation(destinationLocation)
                .searchCriteria(searchCriteria != null ? searchCriteria : TripSearchCriteria.defaultCriteria())
                .searchTime(LocalDateTime.now())
                .tripOptions(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .version(0L)
                .build();

        plan.registerEvent(new TripPlanCreatedEvent(
                plan.id.getValue(),
                originLocation.getLatitudeAsDouble(),
                originLocation.getLongitudeAsDouble(),
                destinationLocation.getLatitudeAsDouble(),
                destinationLocation.getLongitudeAsDouble()
        ));

        return plan;
    }

    public static TripPlan restore(
            TripPlanId id,
            Coordinates originLocation,
            Coordinates destinationLocation,
            TripSearchCriteria searchCriteria,
            LocalDateTime searchTime,
            int optionsCount,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return TripPlan.builder()
                .id(id)
                .originLocation(originLocation)
                .destinationLocation(destinationLocation)
                .searchCriteria(searchCriteria)
                .searchTime(searchTime)
                .tripOptions(new ArrayList<>())
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
    }


    public void addTripOption(TripOption option, TripOptionComparator comparator) {
        if (option == null) {
            throw new IllegalArgumentException("Trip option cannot be null");
        }

        if (tripOptions.size() >= MAX_OPTIONS_PER_PLAN) {
            TripOption worstOption = tripOptions.stream()
                    .max(comparator)
                    .orElse(null);

            if (comparator.isOptionBetter(option, worstOption)) {
                tripOptions.remove(worstOption);
                tripOptions.add(option);
            }
        } else {
            tripOptions.add(option);
        }

        registerEvent(new TripOptionsCalculatedEvent(
                this.id.getValue(),
                tripOptions.size(),
                option.getTripType().name(),
                option.getTotalTravelMinutes()
        ));
    }

    public List<TripOption> getBestOptions(int maxCount, TripOptionComparator comparator) {
        return tripOptions.stream()
                .sorted(comparator)
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
                .min(Comparator.comparing(option -> (option.getTransfersCount() + 1) * 1.0))
                .orElse(null);
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

    public int getTripOptionsCount() {
        return tripOptions.size();
    }

    public List<TripOption> getTripOptions() {
        return new ArrayList<>(tripOptions);
    }

    public TripPlanStatistics getStatistics() {
        if (tripOptions.isEmpty()) {
            return new TripPlanStatistics(0, 0, 0, 0, 0.0);
        }

        int directCount = (int) tripOptions.stream()
                .filter(o -> o.getTripType() == TripType.DIRECT)
                .count();

        int transferCount = tripOptions.size() - directCount;

        int fastestTime = tripOptions.stream()
                .mapToInt(TripOption::getTotalTravelMinutes)
                .min()
                .orElse(0);

        int averageTime = (int) tripOptions.stream()
                .mapToInt(TripOption::getTotalTravelMinutes)
                .average()
                .orElse(0);

        double averageCost = tripOptions.stream()
                .mapToDouble(TripOption::getEstimatedCostManat)
                .average()
                .orElse(0.0);

        return new TripPlanStatistics(
                directCount,
                transferCount,
                fastestTime,
                averageTime,
                averageCost
        );
    }

    @Override
    public TripPlanId getId() {
        return id;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    public record TripPlanStatistics(
            int directOptionsCount,
            int transferOptionsCount,
            int fastestTimeMinutes,
            int averageTimeMinutes,
            double averageCostManat
    ) {}
}
