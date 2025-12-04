package biz.ugur.busroutebackend.transport.application.dto.shift;

public record VehicleShiftsData(
        String vehicleId,
        String vehicleLicensePlate,
        String vehicleDeviceId,
        ShiftAssignmentData firstShift,
        ShiftAssignmentData secondShift
) {
}
