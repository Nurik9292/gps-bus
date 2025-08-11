package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;

import java.util.List;

public record CreateRoute(
        String routeNumber,
        String routeName,
        String nameTm,
        String nameEn,
        String routeColor,
        Integer estimatedDurationMinutes,
        Boolean isActive,
        String cityId,
        List<String> forwardStopIds,
        List<String> backwardStopIds,
        List<List<Double>> forwardGeometry,
        List<List<Double>> backwardGeometry
) {
    public CreateRoute {

        if (forwardStopIds == null) {
            forwardStopIds = List.of();
        }
        if (backwardStopIds == null) {
            backwardStopIds = List.of();
        }
    }

    public boolean hasForwardStops() {
        return forwardStopIds != null && !forwardStopIds.isEmpty();
    }

    public boolean hasBackwardStops() {
        return backwardStopIds != null && !backwardStopIds.isEmpty();
    }

    public boolean hasStops() {
        return hasForwardStops() || hasBackwardStops();
    }
}
