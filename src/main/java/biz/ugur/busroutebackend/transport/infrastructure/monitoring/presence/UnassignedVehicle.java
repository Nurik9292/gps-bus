package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

public record UnassignedVehicle(String licensePlate, String gpsRouteNumber, boolean live, String lastSignalAgo) {
}
