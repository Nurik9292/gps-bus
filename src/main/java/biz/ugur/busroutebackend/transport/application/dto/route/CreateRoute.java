package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;

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
}
