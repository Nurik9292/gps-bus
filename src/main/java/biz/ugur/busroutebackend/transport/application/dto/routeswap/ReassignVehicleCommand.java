package biz.ugur.busroutebackend.transport.application.dto.routeswap;

public record ReassignVehicleCommand(String vehicleId, String routeNumber, String reason) {
}
