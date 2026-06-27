package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

public record EmptyRoute(String routeNumber, EmptyRouteReason reason, int assignedCount) {
}
