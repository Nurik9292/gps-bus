package biz.ugur.busroutebackend.transport.infrastructure.debug;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
@Slf4j
public class PipelineTracer {

    @Value("${ugur.diagnostics.tracked-plates:}")
    private String trackedPlatesProperty;

    @Value("${ugur.diagnostics.tracked-device-ids:}")
    private String trackedDeviceIdsProperty;

    @Value("${ugur.diagnostics.tracked-routes:}")
    private String trackedRoutesProperty;

    private volatile Set<String> trackedPlates = Set.of();
    private volatile Set<String> trackedDeviceIds = Set.of();
    private volatile Set<String> trackedRoutes = Set.of();
    private volatile boolean trackAll = false;

    private final ConcurrentHashMap<String, String> idToRoute = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        trackedPlates = parse(trackedPlatesProperty);
        trackedDeviceIds = parse(trackedDeviceIdsProperty);
        trackedRoutes = parse(trackedRoutesProperty);
        trackAll = trackedPlates.contains("*")
                || trackedDeviceIds.contains("*")
                || trackedRoutes.contains("*");
        if (trackAll) {
            log.warn("[TRACE] PipelineTracer enabled for ALL vehicles — expect very high log volume");
        } else if (!trackedPlates.isEmpty() || !trackedDeviceIds.isEmpty() || !trackedRoutes.isEmpty()) {
            log.info("[TRACE] PipelineTracer enabled plates={} deviceIds={} routes={}",
                    trackedPlates, trackedDeviceIds, trackedRoutes);
        }
    }

    private Set<String> parse(String prop) {
        if (prop == null || prop.isBlank()) return Set.of();
        return Arrays.stream(prop.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAnyTrackingEnabled() {
        return trackAll
                || !trackedPlates.isEmpty()
                || !trackedDeviceIds.isEmpty()
                || !trackedRoutes.isEmpty();
    }

    public void rememberDeviceRoute(String deviceId, String routeNumber) {
        rememberRoute(deviceId, null, routeNumber);
    }

    public void rememberRoute(String deviceId, String vehicleId, String routeNumber) {
        if (routeNumber == null || routeNumber.isBlank()) return;
        if (deviceId != null && !deviceId.isBlank()) {
            idToRoute.put(deviceId, routeNumber);
        }
        if (vehicleId != null && !vehicleId.isBlank()) {
            idToRoute.put(vehicleId, routeNumber);
        }
    }

    public boolean isTrackedByPlate(String licensePlate) {
        if (trackAll) return true;
        return licensePlate != null && !trackedPlates.isEmpty() && trackedPlates.contains(licensePlate);
    }

    public boolean isTrackedByDevice(String deviceId) {
        if (trackAll) return true;
        return deviceId != null && !trackedDeviceIds.isEmpty() && trackedDeviceIds.contains(deviceId);
    }

    public boolean isTrackedByRoute(String routeNumber) {
        if (trackAll) return true;
        return routeNumber != null && !trackedRoutes.isEmpty() && trackedRoutes.contains(routeNumber);
    }

    public boolean isTracked(String licensePlate, String deviceId) {
        return isTracked(licensePlate, deviceId, null);
    }

    public boolean isTracked(String licensePlate, String anyId, String routeNumber) {
        if (trackAll) return true;
        if (isTrackedByPlate(licensePlate)) return true;
        if (isTrackedByDevice(anyId)) return true;
        if (isTrackedByRoute(routeNumber)) return true;
        if (anyId != null && !trackedRoutes.isEmpty()) {
            String cachedRoute = idToRoute.get(anyId);
            if (cachedRoute != null && trackedRoutes.contains(cachedRoute)) return true;
        }
        return false;
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

    public void traceOutlierDecision(String deviceId, String plate, String decision,
                                      double distFromLastGpsM, long elapsedMs, double maxAllowedM) {
        if (!isTracked(plate, deviceId)) return;
        log.info("[TRACE_OUTLIER] device={} plate={} decision={} distFromLastGps={}m elapsed={}ms maxAllowed={}m",
                deviceId, plate, decision,
                String.format("%.0f", distFromLastGpsM),
                elapsedMs,
                String.format("%.0f", maxAllowedM));
    }

    public void traceSnap(String vehicleId, String plate, String routeNumber, int dir,
                          double snapDistM, double frac, boolean snapped, String branch) {
        if (!isTracked(plate, vehicleId, routeNumber)) return;
        log.info("[TRACE_SNAP] vehicle={} plate={} route={} dir={} branch={} snapped={} snapDist={}m frac={}",
                vehicleId, plate, routeNumber, dir, branch, snapped,
                String.format("%.1f", snapDistM),
                String.format("%.4f", frac));
    }

    public void traceDbSave(String deviceId, String plate, Double oldLat, Double oldLon,
                             Double newLat, Double newLon, boolean willBeWritten, String reason) {
        if (!isTracked(plate, deviceId)) return;
        log.info("[TRACE_DB_SAVE] device={} plate={} old=({},{}) new=({},{}) willBeWritten={} reason={}",
                deviceId, plate, oldLat, oldLon, newLat, newLon, willBeWritten, reason);
    }

    public void traceDbReadVehicle(String vehicleId, String deviceId, String plate, String routeNumber,
                                    Integer directionFromDb, Double lat, Double lon,
                                    LocalDateTime lastPositionUpdate, Long version) {
        if (!isTracked(plate, deviceId, routeNumber)) return;
        log.info("[TRACE_DB_READ_VEHICLE] vehicle={} device={} plate={} route={} dirFromDb={} lat={} lon={} lastUpd={} ver={}",
                vehicleId, deviceId, plate, routeNumber, directionFromDb, lat, lon, lastPositionUpdate, version);
    }

    public void traceDbWriteVehicle(String vehicleId, String deviceId, String plate, String routeNumber,
                                     Integer newDirection, Double lat, Double lon, String source, Long expectedVersion) {
        if (!isTracked(plate, deviceId, routeNumber)) return;
        log.info("[TRACE_DB_WRITE_VEHICLE] vehicle={} device={} plate={} route={} newDir={} lat={} lon={} source={} expectedVer={}",
                vehicleId, deviceId, plate, routeNumber, newDirection, lat, lon, source, expectedVersion);
    }

    public void traceGpsHistoryWrite(String deviceId, Double lat, Double lon, Double speed, LocalDateTime fixTime) {
        if (!isTracked(null, deviceId)) return;
        log.info("[TRACE_GPS_HISTORY_WRITE] device={} lat={} lon={} speed={} fixTime={}",
                deviceId, lat, lon, speed, fixTime);
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

    public void traceWsPayload(String vehicleId, String plate, String routeNumber,
                                  Double lat, Double lon,
                                  Double course, Integer direction,
                                  Double speedKmh, Boolean isInMotion,
                                  Boolean predicted, String confidence,
                                  double prevSnapLat, double prevSnapLon,
                                  double motionCourseDeg, double stateCourse) {
        if (!isTracked(plate, vehicleId, routeNumber)) return;
        log.info("[TRACE_WS_PAYLOAD] vehicle={} plate={} route={} ws_lat={} ws_lon={} ws_course={} ws_direction={} ws_speed={} ws_inMotion={} ws_predicted={} ws_confidence={} prev_snap=({},{}) motionCourseDeg={} stateCourse={}",
                vehicleId, plate, routeNumber,
                lat, lon, course, direction,
                speedKmh, isInMotion, predicted, confidence,
                Double.isNaN(prevSnapLat) ? "-" : String.format("%.6f", prevSnapLat),
                Double.isNaN(prevSnapLon) ? "-" : String.format("%.6f", prevSnapLon),
                Double.isNaN(motionCourseDeg) ? "NaN" : String.format("%.1f", motionCourseDeg),
                String.format("%.1f", stateCourse));
    }

    public void traceNearestStops(String vehicleId, String plate, String routeNumber,
                                    Integer currentDirection, Double gpsCourse, Double speedKmh,
                                    Integer chosenDirection, Integer chosenSequence,
                                    Double chosenDistanceMeters, Double oppositeDistanceMeters) {
        if (!isTracked(plate, vehicleId, routeNumber)) return;
        log.info("[TRACE_DIR_NEAREST_STOPS] vehicle={} plate={} route={} currentDir={} gpsCourse={}° speed={}km/h chosenDir={} chosenSeq={} chosenDist={}m oppositeDist={}m",
                vehicleId, plate, routeNumber,
                currentDirection,
                gpsCourse != null ? String.format("%.0f", gpsCourse) : "-",
                speedKmh != null ? String.format("%.1f", speedKmh) : "-",
                chosenDirection, chosenSequence,
                chosenDistanceMeters != null ? String.format("%.1f", chosenDistanceMeters) : "-",
                oppositeDistanceMeters != null ? String.format("%.1f", oppositeDistanceMeters) : "-");
    }

    public void traceStateWrite(String vehicleId, String plate, String routeNumber, String reason,
                                  double predictedLat, double predictedLon,
                                  double gpsLat, double gpsLon,
                                  double fractionOnRoute, boolean inMotion, double speedKmh,
                                  int direction, boolean directionConfirmed,
                                  Instant coldStartUntilAt) {
        if (!isTracked(plate, vehicleId, routeNumber)) return;
        log.info("[TRACE_STATE_WRITE] vehicle={} plate={} route={} reason={} predicted=({},{}) gps=({},{}) frac={} inMotion={} speed={} dir={} dirConfirmed={} coldStartUntil={}",
                vehicleId, plate, routeNumber, reason,
                String.format("%.5f", predictedLat), String.format("%.5f", predictedLon),
                String.format("%.5f", gpsLat), String.format("%.5f", gpsLon),
                fractionOnRoute >= 0 ? String.format("%.4f", fractionOnRoute) : "-",
                inMotion, String.format("%.1f", speedKmh),
                direction, directionConfirmed,
                coldStartUntilAt);
    }

    public void traceWsInitialSnapshotRead(String sessionId, String subscriptionType, Object filter, int count) {
        if (!isAnyTrackingEnabled()) return;
        log.info("[TRACE_WS_INITIAL_SNAPSHOT_READ] session={} subscription={} filter={} count={}",
                sessionId, subscriptionType, filter, count);
    }

    public void traceWsDroppedBySubscription(String vehicleId, String plate, String routeNumber,
                                              String subscriptionType, Object filter) {
        if (!isTracked(plate, vehicleId, routeNumber)) return;
        log.info("[TRACE_WS_DROPPED_BY_SUBSCRIPTION] vehicle={} plate={} route={} subscription={} filter={}",
                vehicleId, plate, routeNumber, subscriptionType, filter);
    }

    public void traceSnapStateDirectionMutation(String vehicleId, String plate, String routeNumber,
                                                  int inputDirection, int outputDirection,
                                                  String branch, double fracIn, double fracOut,
                                                  double snapDistMeters) {
        if (!isTracked(plate, vehicleId, routeNumber)) return;
        log.warn("[TRACE_SNAP_STATE_DIR_FLIP] vehicle={} plate={} route={} inputDir={} outputDir={} branch={} fracIn={} fracOut={} snapDist={}m — state.direction flipped by snap; Vehicle.currentDirection NOT updated by this code path, next batchUpdate may revert in DB",
                vehicleId, plate, routeNumber,
                inputDirection, outputDirection, branch,
                fracIn >= 0 ? String.format("%.4f", fracIn) : "-",
                fracOut >= 0 ? String.format("%.4f", fracOut) : "-",
                String.format("%.1f", snapDistMeters));
    }
}
