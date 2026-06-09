package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.OffRouteRecord;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class FleetPresenceAlertMonitor {

    static Optional<AssignedVehicleStatus> classify(LocalDateTime lastPositionUpdate,
                                                    Optional<OffRouteRecord> offRoute,
                                                    LocalDateTime nowLocal,
                                                    ShiftType shift,
                                                    FleetPresenceAlertProperties props) {
        if (offRoute.isPresent()) {
            return Optional.of(AssignedVehicleStatus.OFF_ROUTE);
        }

        LocalDateTime shiftStart = nowLocal.toLocalDate().atTime(shift.getStartTime());

        if (lastPositionUpdate == null || lastPositionUpdate.isBefore(shiftStart)) {
            long minutesSinceShiftStart = Duration.between(shiftStart, nowLocal).toMinutes();
            if (minutesSinceShiftStart < props.getStartupGraceMinutes()) {
                return Optional.empty();
            }
            return Optional.of(AssignedVehicleStatus.NOT_STARTED);
        }

        LocalDateTime freshCutoff = nowLocal.minusMinutes(props.getSilentThresholdMinutes());
        if (lastPositionUpdate.isAfter(freshCutoff)) {
            return Optional.empty();
        }
        return Optional.of(AssignedVehicleStatus.WENT_SILENT);
    }
}
