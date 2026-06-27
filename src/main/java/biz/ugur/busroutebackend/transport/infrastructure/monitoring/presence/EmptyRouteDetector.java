package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class EmptyRouteDetector {

    private EmptyRouteDetector() {
    }

    public static List<EmptyRoute> detect(List<BusRoute> activeRoutes,
                                          List<RouteAssignment> currentShiftAssignments,
                                          List<Vehicle> activeVehicles,
                                          LocalDateTime nowLocal,
                                          int silentThresholdMinutes,
                                          LocalDateTime graceCutoff) {
        if (nowLocal.isBefore(graceCutoff)) {
            return List.of();
        }

        LocalDateTime freshCutoff = nowLocal.minusMinutes(silentThresholdMinutes);

        Map<String, Long> assignmentCountByRouteId = currentShiftAssignments.stream()
                .collect(Collectors.groupingBy(a -> a.getRouteId().getValue(), Collectors.counting()));

        List<EmptyRoute> result = new ArrayList<>();
        for (BusRoute route : activeRoutes) {
            boolean hasLiveBus = activeVehicles.stream()
                    .anyMatch(v -> isLive(v, freshCutoff) && servesRoute(v, route));
            if (hasLiveBus) {
                continue;
            }
            long assignedCount = assignmentCountByRouteId.getOrDefault(route.getId().getValue(), 0L);
            EmptyRouteReason reason = assignedCount > 0
                    ? EmptyRouteReason.ASSIGNED_BUT_SILENT
                    : EmptyRouteReason.NOT_ASSIGNED;
            result.add(new EmptyRoute(route.getRouteNumber(), reason, (int) assignedCount));
        }
        return result;
    }

    private static boolean isLive(Vehicle vehicle, LocalDateTime freshCutoff) {
        LocalDateTime last = vehicle.getLastPositionUpdate();
        return last != null && last.isAfter(freshCutoff);
    }

    private static boolean servesRoute(Vehicle vehicle, BusRoute route) {
        BusRouteId assigned = vehicle.getAssignedRouteId();
        if (assigned != null && assigned.getValue().equals(route.getId().getValue())) {
            return true;
        }
        String routeNumber = vehicle.getRouteNumber();
        return routeNumber != null && !routeNumber.isBlank() && routeNumber.equals(route.getRouteNumber());
    }
}
