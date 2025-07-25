package biz.ugur.busroutebackend.transport.infrastructure.messaging;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class VehicleRouteAssignmentMessage {

    @JsonProperty("vehicle_id")
    private final String vehicleId;

    @JsonProperty("license_plate")
    private final String licensePlate;

    @JsonProperty("previous_route_id")
    private final String previousRouteId;

    @JsonProperty("new_route_id")
    private final String newRouteId;

    @JsonProperty("assignment_time")
    private final Instant assignmentTime;

    @JsonProperty("message_type")
    private final String messageType = "vehicle_route_assignment";

    public VehicleRouteAssignmentMessage(String vehicleId, String licensePlate,
                                         String previousRouteId, String newRouteId,
                                         Instant assignmentTime) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.previousRouteId = previousRouteId;
        this.newRouteId = newRouteId;
        this.assignmentTime = assignmentTime;
    }
}