package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.shift;

import biz.ugur.busroutebackend.transport.application.dto.shift.VehicleShiftsData;
import com.fasterxml.jackson.annotation.JsonProperty;

public record VehicleShiftsResponse(
        @JsonProperty("vehicle_id") String vehicleId,
        @JsonProperty("vehicle_license_plate") String vehicleLicensePlate,
        @JsonProperty("vehicle_device_id") String vehicleDeviceId,
        @JsonProperty("first_shift") ShiftAssignmentResponse firstShift,
        @JsonProperty("second_shift") ShiftAssignmentResponse secondShift
) {
    public static VehicleShiftsResponse fromData(VehicleShiftsData data) {
        return new VehicleShiftsResponse(
                data.vehicleId(),
                data.vehicleLicensePlate(),
                data.vehicleDeviceId(),
                data.firstShift() != null ? ShiftAssignmentResponse.fromData(data.firstShift()) : null,
                data.secondShift() != null ? ShiftAssignmentResponse.fromData(data.secondShift()) : null
        );
    }
}
