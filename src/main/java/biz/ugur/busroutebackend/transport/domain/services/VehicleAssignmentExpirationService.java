package biz.ugur.busroutebackend.transport.domain.services;

import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;

import java.time.Instant;

public class VehicleAssignmentExpirationService {


    public boolean hasExpiredAssignment(Vehicle vehicle, RouteAssignment assignment) {
        if (vehicle == null || !vehicle.hasAssignedRoute()) {
            return false;
        }

        if (assignment == null) {
            return false;
        }

        return assignment.isExpired();
    }


    public boolean isExpired(RouteAssignment assignment) {
        if (assignment == null) {
            return false;
        }

        return assignment.isExpired();
    }


    public boolean shouldClearRoute(Vehicle vehicle, Instant expiresAt) {
        if (vehicle == null || !vehicle.hasAssignedRoute()) {
            return false;
        }

        if (expiresAt == null) {
            return false;
        }

        return Instant.now().isAfter(expiresAt);
    }

    public Vehicle clearExpiredAssignment(Vehicle vehicle) {
        if (vehicle == null || !vehicle.hasAssignedRoute()) {
            return vehicle;
        }

        return vehicle.clearRouteAssignment();
    }
}
