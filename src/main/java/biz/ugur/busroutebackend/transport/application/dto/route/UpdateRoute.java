package biz.ugur.busroutebackend.transport.application.dto.route;

import java.util.List;

public record UpdateRoute(
        String routeId,
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
