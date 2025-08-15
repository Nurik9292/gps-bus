package biz.ugur.busroutebackend.transport.application.dto.route;

import biz.ugur.busroutebackend.transport.domain.valueobject.RouteStopInfo;

import java.util.List;

public record RouteStops(
        String routeId,
        List<RouteStopInfo> stops,
        Integer totalStops
) {}