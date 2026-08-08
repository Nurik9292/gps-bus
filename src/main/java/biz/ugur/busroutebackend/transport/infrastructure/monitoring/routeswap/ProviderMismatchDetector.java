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

    public static List<Mismatch> detect(List<BusInfoDTO> providerRegistry, List<Vehicle> vehicles) {
        Map<String, String> providerByPlate = new HashMap<>();
        for (BusInfoDTO entry : providerRegistry) {
            if (entry.getCarNumber() != null && entry.getRouteNumber() != null
                    && !entry.getRouteNumber().isBlank()) {
                providerByPlate.put(entry.getCarNumber().trim(), entry.getRouteNumber().trim());
            }
        }
        List<Mismatch> mismatches = new ArrayList<>();
        for (Vehicle vehicle : vehicles) {
            if (!Boolean.TRUE.equals(vehicle.getIsActive()) || vehicle.getLicensePlate() == null) {
                continue;
            }
            String providerRoute = providerByPlate.get(vehicle.getLicensePlate().trim());
            if (providerRoute == null) {
                continue;
            }
            String dbRoute = vehicle.getRouteNumber();
            if (dbRoute == null || dbRoute.isBlank() || !dbRoute.trim().equals(providerRoute)) {
                mismatches.add(new Mismatch(
                        vehicle.getLicensePlate(),
                        vehicle.getId() != null ? vehicle.getId().getValue() : null,
                        dbRoute == null || dbRoute.isBlank() ? null : dbRoute.trim(),
                        providerRoute));
            }
        }
        return mismatches;
    }
}
