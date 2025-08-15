package biz.ugur.busroutebackend.transport.application.dto.route;

import java.time.Instant;

public record RouteDetail(
        String id,
        String routeNumber,
        String routeName,
        String routeNameEn,
        String routeNameTm,
        String routeColor,
        Boolean isActive,
        Integer totalDistanceForwardMeters,
        Integer totalDistanceBackwardMeters,
        Instant createdAt,
        Instant updatedAt
) {
}
