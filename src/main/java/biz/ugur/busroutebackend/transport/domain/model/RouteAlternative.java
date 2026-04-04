package biz.ugur.busroutebackend.transport.domain.model;

import biz.ugur.busroutebackend.shared.base.BaseEntity;
import biz.ugur.busroutebackend.shared.domain.entity.Entity;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteAlternativeId;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.time.LocalDateTime;


@Builder(toBuilder = true)
@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteAlternative extends Entity<RouteAlternativeId> implements BaseEntity<RouteAlternativeId> {

    private final RouteAlternativeId id;
    private final BusRouteId primaryRouteId;
    private final BusRouteId alternativeRouteId;
    private final Integer priority;
    private final String description;
    private final String descriptionTm;
    private final String descriptionEn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long version;

    public static RouteAlternative create(
            BusRouteId primaryRouteId,
            BusRouteId alternativeRouteId,
            Integer priority,
            String description,
            String descriptionTm,
            String descriptionEn) {

        validateRoutes(primaryRouteId, alternativeRouteId);

        return builder()
                .id(RouteAlternativeId.generate())
                .primaryRouteId(primaryRouteId)
                .alternativeRouteId(alternativeRouteId)
                .priority(priority != null ? priority : 1)
                .description(trimOrNull(description))
                .descriptionTm(trimOrNull(descriptionTm))
                .descriptionEn(trimOrNull(descriptionEn))
                .version(0L)
                .build();
    }

    public static RouteAlternative restore(
            RouteAlternativeId id,
            BusRouteId primaryRouteId,
            BusRouteId alternativeRouteId,
            Integer priority,
            String description,
            String descriptionTm,
            String descriptionEn,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Long version) {

        return builder()
                .id(id)
                .primaryRouteId(primaryRouteId)
                .alternativeRouteId(alternativeRouteId)
                .priority(priority != null ? priority : 1)
                .description(description)
                .descriptionTm(descriptionTm)
                .descriptionEn(descriptionEn)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .version(version != null ? version : 0L)
                .build();
    }

    public RouteAlternative update(
            Integer priority,
            String description,
            String descriptionTm,
            String descriptionEn) {

        return this.toBuilder()
                .priority(priority != null ? priority : this.priority)
                .description(trimOrNull(description))
                .descriptionTm(trimOrNull(descriptionTm))
                .descriptionEn(trimOrNull(descriptionEn))
                .build();
    }

    public RouteAlternative updatePriority(Integer priority) {
        if (priority == null || priority < 1) {
            throw new IllegalArgumentException("Priority must be a positive integer");
        }
        return this.toBuilder().priority(priority).build();
    }

    private static void validateRoutes(BusRouteId primaryRouteId, BusRouteId alternativeRouteId) {
        if (primaryRouteId == null) {
            throw new IllegalArgumentException("Primary route ID cannot be null");
        }
        if (alternativeRouteId == null) {
            throw new IllegalArgumentException("Alternative route ID cannot be null");
        }
        if (primaryRouteId.getValue().equals(alternativeRouteId.getValue())) {
            throw new IllegalArgumentException("Primary and alternative routes cannot be the same");
        }
    }

    private static String trimOrNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    @Override
    public RouteAlternativeId getId() {
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

    @Override
    public String toString() {
        return "RouteAlternative{" +
                "id=" + id +
                ", primaryRouteId=" + primaryRouteId +
                ", alternativeRouteId=" + alternativeRouteId +
                ", priority=" + priority +
                '}';
    }
}
