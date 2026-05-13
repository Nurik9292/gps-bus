package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;

import java.time.LocalDate;

public record OffRouteAlertKey(String vehicleId, LocalDate date, ShiftType shift) {
}
