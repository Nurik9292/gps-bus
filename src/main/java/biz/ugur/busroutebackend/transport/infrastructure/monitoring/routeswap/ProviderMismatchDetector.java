package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ProviderMismatchDetector {

    public record Mismatch(String licensePlate, String vehicleId,
                           String dbRouteNumber, String providerRouteNumber) {
    }

    private ProviderMismatchDetector() {
    }

    private static String normalizePlate(String plate) {
        return plate.trim().replaceAll("\\s+", " ").toUpperCase();
    }

    private static boolean isOnLine(Vehicle vehicle) {
        return vehicle.hasRecentPosition(900) && !vehicle.isCurrentlyInGarage();
    }

    public static List<Mismatch> detect(List<BusInfoDTO> providerRegistry, List<Vehicle> vehicles) {
        Map<String, String> providerByPlate = new HashMap<>();
        for (BusInfoDTO entry : providerRegistry) {
            if (entry.getCarNumber() != null && entry.getRouteNumber() != null
                    && !entry.getRouteNumber().isBlank()) {
                providerByPlate.put(normalizePlate(entry.getCarNumber()), entry.getRouteNumber().trim());
            }
        }
        List<Mismatch> mismatches = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (!Boolean.TRUE.equals(vehicle.getIsActive()) || vehicle.getLicensePlate() == null) {
                continue;
            }
            String providerRoute = providerByPlate.get(normalizePlate(vehicle.getLicensePlate()));
            if (providerRoute == null) {
                continue;
            }
            String dbRoute = vehicle.getRouteNumber();
            boolean unassigned = dbRoute == null || dbRoute.isBlank();
            if (unassigned && !isOnLine(vehicle)) {
                continue;
            }
            if (unassigned || !dbRoute.trim().equals(providerRoute)) {
                mismatches.add(new Mismatch(
                        vehicle.getLicensePlate(),
                        vehicle.getId() != null ? vehicle.getId().getValue() : null,
                        unassigned ? null : dbRoute.trim(),
                        providerRoute));
            }
        }
        return mismatches;
    }
}
