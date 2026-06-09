package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class OffRouteStateRegistry {

    private final ConcurrentMap<OffRouteAlertKey, OffRouteRecord> records = new ConcurrentHashMap<>();

    public void record(String vehicleId, LocalDate date, ShiftType shift, OffRouteRecord record) {
        records.putIfAbsent(new OffRouteAlertKey(vehicleId, date, shift), record);
    }

    public Optional<OffRouteRecord> find(String vehicleId, LocalDate date, ShiftType shift) {
        return Optional.ofNullable(records.get(new OffRouteAlertKey(vehicleId, date, shift)));
    }

    public void cleanupBefore(LocalDate cutoff) {
        records.keySet().removeIf(key -> key.date().isBefore(cutoff));
    }
}
