package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class UnassignedVehicleDetector {

    private UnassignedVehicleDetector() {
    }

    public static List<UnassignedVehicle> detect(List<Vehicle> activeVehicles,
                                                 Set<String> assignedVehicleIdsToday,
                                                 LocalDateTime nowLocal,
                                                 int silentThresholdMinutes) {
        LocalDateTime freshCutoff = nowLocal.minusMinutes(silentThresholdMinutes);
        List<UnassignedVehicle> result = new ArrayList<>();
        for (Vehicle vehicle : activeVehicles) {
            String vehicleId = vehicle.getId().getValue();
            if (assignedVehicleIdsToday.contains(vehicleId)) {
                continue;
            }
            String plate = vehicle.getLicensePlate() != null ? vehicle.getLicensePlate() : vehicleId;
            String gpsRoute = gpsRouteOf(vehicle);
            LocalDateTime last = vehicle.getLastPositionUpdate();
            boolean live = last != null && last.isAfter(freshCutoff);
            result.add(new UnassignedVehicle(plate, gpsRoute, live, signalAgo(last, nowLocal)));
        }
        return result;
    }

    private static String gpsRouteOf(Vehicle vehicle) {
        String routeNumber = vehicle.getRouteNumber();
        return routeNumber != null && !routeNumber.isBlank() ? routeNumber : null;
    }

    private static String signalAgo(LocalDateTime last, LocalDateTime nowLocal) {
        if (last == null) {
            return "никогда";
        }
        Duration d = Duration.between(last, nowLocal);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return (hours > 0 ? hours + "ч " : "") + minutes + "м назад";
    }
}
