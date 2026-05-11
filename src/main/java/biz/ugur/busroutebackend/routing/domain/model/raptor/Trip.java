package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class Trip extends AggregateRoot<Trip, TripId> {

    private final TripId id;
    private final BusRouteId routeId;
    private final RouteDirection direction;
    private final String serviceId;
    private final Integer headwaySeconds;
    private final LocalTime startTime;
    private final LocalTime endTime;
    private final Boolean isActive;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    @Override
    public TripId getId() {
        return id;
    }

    @Override
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public Long getVersion() {
        return version;
    }

    @Override
    public void setVersion(Long version) {
        this.version = version;
    }

    public static Trip frequencyBased(BusRouteId routeId,
                                       RouteDirection direction,
                                       String serviceId,
                                       int headwaySeconds,
                                       LocalTime startTime,
                                       LocalTime endTime) {
        validateTimeWindow(startTime, endTime);
        validateHeadway(headwaySeconds);
        validateServiceId(serviceId);

        return builder()
                .id(TripId.generate())
                .routeId(routeId)
                .direction(direction)
                .serviceId(serviceId)
                .headwaySeconds(headwaySeconds)
                .startTime(startTime)
                .endTime(endTime)
                .isActive(true)
                .version(0L)
                .build();
    }

    public static Trip restore(TripId id,
                                BusRouteId routeId,
                                RouteDirection direction,
                                String serviceId,
                                Integer headwaySeconds,
                                LocalTime startTime,
                                LocalTime endTime,
                                Boolean isActive,
                                LocalDateTime createdAt,
                                LocalDateTime updatedAt,
                                Long version) {
        return builder()
                .id(id)
                .routeId(routeId)
                .direction(direction)
                .serviceId(serviceId)
                .headwaySeconds(headwaySeconds)
                .startTime(startTime)
                .endTime(endTime)
                .isActive(isActive)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version)
                .build();
    }

    public boolean isFrequencyBased() {
        return headwaySeconds != null;
    }

    private static void validateTimeWindow(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Trip start_time and end_time are required");
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(
                    "Trip end_time (" + end + ") must be strictly after start_time (" + start + ")");
        }
    }

    private static void validateHeadway(int headwaySeconds) {
        if (headwaySeconds <= 0) {
            throw new IllegalArgumentException(
                    "Frequency-based trip headway_seconds must be positive, got " + headwaySeconds);
        }
    }

    private static void validateServiceId(String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            throw new IllegalArgumentException("Trip service_id cannot be blank");
        }
    }
}
