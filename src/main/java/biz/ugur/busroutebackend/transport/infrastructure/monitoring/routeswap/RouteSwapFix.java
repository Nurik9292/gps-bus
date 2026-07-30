package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import java.time.Instant;

public record RouteSwapFix(
        String vehicleId,
        String licensePlate,
        String routeId,
        String routeNumber,
        double latitude,
        double longitude,
        double speedKmh,
        boolean inGarage,
        Instant timestamp) {
}
