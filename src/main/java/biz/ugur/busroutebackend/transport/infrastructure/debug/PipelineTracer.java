package biz.ugur.busroutebackend.transport.infrastructure.debug;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PipelineTracer {

    @Value("${ugur.diagnostics.tracked-plates:}")
    private String trackedPlatesProperty;

    @Value("${ugur.diagnostics.tracked-device-ids:}")
    private String trackedDeviceIdsProperty;

    private volatile Set<String> trackedPlates = Set.of();
    private volatile Set<String> trackedDeviceIds = Set.of();
    private volatile boolean trackAll = false;

    @PostConstruct
    void init() {
        trackedPlates = parse(trackedPlatesProperty);
        trackedDeviceIds = parse(trackedDeviceIdsProperty);
        trackAll = trackedPlates.contains("*") || trackedDeviceIds.contains("*");
        if (trackAll) {
            log.warn("[TRACE] PipelineTracer enabled for ALL vehicles — expect very high log volume");
        } else if (!trackedPlates.isEmpty() || !trackedDeviceIds.isEmpty()) {
            log.info("[TRACE] PipelineTracer enabled plates={} deviceIds={}", trackedPlates, trackedDeviceIds);
        }
    }

    private Set<String> parse(String prop) {
        if (prop == null || prop.isBlank()) return Set.of();
        return Arrays.stream(prop.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isTrackedByPlate(String licensePlate) {
        if (trackAll) return true;
        return licensePlate != null && !trackedPlates.isEmpty() && trackedPlates.contains(licensePlate);
    }

    public boolean isTrackedByDevice(String deviceId) {
        if (trackAll) return true;
        return deviceId != null && !trackedDeviceIds.isEmpty() && trackedDeviceIds.contains(deviceId);
    }

    public boolean isTracked(String licensePlate, String deviceId) {
        return trackAll || isTrackedByPlate(licensePlate) || isTrackedByDevice(deviceId);
    }

    public void traceGpsApiRecv(String deviceId, String plate, String provider,
                                 Double lat, Double lon, String fixTime, Double speed) {
        if (!isTracked(plate, deviceId)) return;
        log.info("[TRACE_GPS_API_RECV] device={} plate={} provider={} lat={} lon={} fixTime={} speed={}",
                deviceId, plate, provider, lat, lon, fixTime, speed);
    }

    public void traceBatchWinner(String deviceId, String plate, String winnerProvider,
                                  int candidatesCount, String winnerFixTime) {
        if (!isTracked(plate, deviceId)) return;
        log.info("[TRACE_BATCH_WINNER] device={} plate={} winner={} candidates={} fixTime={}",
                deviceId, plate, winnerProvider, candidatesCount, winnerFixTime);
    }

    public void traceOutlierDecision(String vehicleId, String plate, String decision,
                                      double distFromLastGpsM, long elapsedMs, double maxAllowedM) {
        if (!isTracked(plate, vehicleId)) return;
        log.info("[TRACE_OUTLIER] vehicle={} plate={} decision={} distFromLastGps={}m elapsed={}ms maxAllowed={}m",
                vehicleId, plate, decision,
                String.format("%.0f", distFromLastGpsM),
                elapsedMs,
                String.format("%.0f", maxAllowedM));
    }

    public void traceSnap(String vehicleId, String plate, String route, int dir,
                          double snapDistM, double frac, boolean snapped, String branch) {
        if (!isTracked(plate, vehicleId)) return;
        log.info("[TRACE_SNAP] vehicle={} plate={} route={} dir={} branch={} snapped={} snapDist={}m frac={}",
                vehicleId, plate, route, dir, branch, snapped,
                String.format("%.1f", snapDistM),
                String.format("%.4f", frac));
    }

    public void traceDbSave(String deviceId, String plate, Double oldLat, Double oldLon,
                             Double newLat, Double newLon, boolean willBeWritten, String reason) {
        if (!isTracked(plate, deviceId)) return;
        log.info("[TRACE_DB_SAVE] device={} plate={} old=({},{}) new=({},{}) willBeWritten={} reason={}",
                deviceId, plate, oldLat, oldLon, newLat, newLon, willBeWritten, reason);
    }

    public void traceWsBroadcast(String vehicleId, String plate, double lat, double lon,
                                  double speed, boolean inMotion, Boolean predicted, String mode) {
        if (!isTracked(plate, vehicleId)) return;
        log.info("[TRACE_WS_BROADCAST] vehicle={} plate={} lat={} lon={} speed={}km/h inMotion={} predicted={} mode={}",
                vehicleId, plate,
                String.format("%.5f", lat),
                String.format("%.5f", lon),
                String.format("%.1f", speed),
                inMotion, predicted, mode);
    }

    public void traceBroadcastSuppressed(String vehicleId, String plate, String reason) {
        if (!isTracked(plate, vehicleId)) return;
        log.info("[TRACE_WS_BROADCAST_SUPPRESSED] vehicle={} plate={} reason={}",
                vehicleId, plate, reason);
    }

    public void traceStage(String stage, String vehicleId, String plate, String detail) {
        if (!isTracked(plate, vehicleId)) return;
        log.info("[TRACE_{}] vehicle={} plate={} {}", stage, vehicleId, plate, detail);
    }
}
