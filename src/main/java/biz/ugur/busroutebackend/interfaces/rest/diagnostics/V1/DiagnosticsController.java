package biz.ugur.busroutebackend.interfaces.rest.diagnostics.V1;

import biz.ugur.busroutebackend.shared.infrastructure.web.ApiVersionConfig;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsPoint;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.time.ZoneOffset;
import java.util.List;

@RestController
@RequestMapping(ApiVersionConfig.V1_DIAGNOSTICS)
@Slf4j
public class DiagnosticsController {

    private static final int DEFAULT_TRAIL_LIMIT_POINTS = 200;
    private static final int MAX_TRAIL_MINUTES = 60;

    private final VehiclePositionPredictionService predictionService;
    private final VehicleRepository vehicleRepository;
    private final VehicleGpsHistoryService gpsHistoryService;
    private final DatabaseClient databaseClient;
    private final ObjectMapper objectMapper;

    public DiagnosticsController(VehiclePositionPredictionService predictionService,
                                  VehicleRepository vehicleRepository,
                                  VehicleGpsHistoryService gpsHistoryService,
                                  DatabaseClient databaseClient,
                                  ObjectMapper objectMapper) {
        this.predictionService = predictionService;
        this.vehicleRepository = vehicleRepository;
        this.gpsHistoryService = gpsHistoryService;
        this.databaseClient = databaseClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/vehicle-snapshot")
    public Flux<VehicleSnapshotItem> vehicleSnapshot(@RequestParam(value = "route", required = false) String routeNumber) {
        List<VehiclePredictionState> states = predictionService.getActiveStates();
        return Flux.fromIterable(states)
                .filter(s -> routeNumber == null || routeNumber.isBlank()
                        || routeNumber.equals(s.getRouteNumber()))
                .map(s -> new VehicleSnapshotItem(
                        s.getVehicleId(),
                        s.getLicensePlate(),
                        s.getRouteNumber(),
                        s.getGpsLatitude(),
                        s.getGpsLongitude(),
                        s.getPredictedLatitude(),
                        s.getPredictedLongitude(),
                        s.getDirection(),
                        s.isDirectionConfirmed(),
                        s.getCourse(),
                        s.getFractionOnRoute() >= 0 ? s.getFractionOnRoute() : null,
                        s.isOffRoute(),
                        s.isInMotion(),
                        s.getRawGpsSpeedKmh(),
                        s.getLastReceivedAt() != null ? s.getLastReceivedAt().toEpochMilli() : null
                ));
    }

    @GetMapping("/gps-trail")
    public Flux<GpsTrailItem> gpsTrail(@RequestParam("route") String routeNumber,
                                        @RequestParam(value = "minutes", defaultValue = "10") int minutes) {
        if (routeNumber == null || routeNumber.isBlank()) {
            return Flux.empty();
        }
        int effectiveMinutes = Math.min(Math.max(1, minutes), MAX_TRAIL_MINUTES);
        long cutoffEpochMs = System.currentTimeMillis() - (long) effectiveMinutes * 60_000L;
        return vehicleRepository.findByRouteNumber(routeNumber)
                .flatMap(vehicle -> gpsHistoryService.getHistoryList(vehicle.getDeviceId(), DEFAULT_TRAIL_LIMIT_POINTS)
                        .map(points -> trailFor(vehicle, points, cutoffEpochMs)))
                .filter(item -> !item.points().isEmpty());
    }

    @GetMapping("/stops")
    public Flux<StopItem> stops() {
        String sql = """
                SELECT id, stop_name, latitude, longitude
                  FROM bus_stops
                 WHERE is_active = true
                """;
        return databaseClient.sql(sql)
                .map((row, meta) -> new StopItem(
                        row.get("id", String.class),
                        row.get("stop_name", String.class),
                        row.get("latitude", Double.class),
                        row.get("longitude", Double.class)
                ))
                .all();
    }

    @GetMapping("/route-geometry")
    public Flux<RouteGeometryItem> routeGeometry(@RequestParam(value = "route", required = false) String routeNumber) {
        boolean hasFilter = routeNumber != null && !routeNumber.isBlank();
        String sql = """
                SELECT route_number, dir, ST_AsGeoJSON(geom) AS geom_json
                  FROM (
                      SELECT route_number, 0 AS dir, geometry_forward AS geom
                        FROM bus_routes WHERE is_active = true AND geometry_forward IS NOT NULL
                      UNION ALL
                      SELECT route_number, 1 AS dir, geometry_backward AS geom
                        FROM bus_routes WHERE is_active = true AND geometry_backward IS NOT NULL
                  ) t
                 WHERE (:routeFilter IS NULL OR route_number = :routeFilter)
                """;
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        spec = hasFilter ? spec.bind("routeFilter", routeNumber) : spec.bindNull("routeFilter", String.class);
        return spec
                .map((row, meta) -> new RouteGeometryItem(
                        row.get("route_number", String.class),
                        row.get("dir", Integer.class),
                        parseGeoJson(row.get("geom_json", String.class))
                ))
                .all();
    }

    private JsonNode parseGeoJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("[DIAGNOSTICS] failed to parse route geometry GeoJSON: {}", e.getMessage());
            return null;
        }
    }

    @GetMapping("/route-stops")
    public Flux<RouteStopItem> routeStops(@RequestParam("route") String routeNumber,
                                          @RequestParam(required = false) String cityId) {
        if (routeNumber == null || routeNumber.isBlank()) {
            return Flux.empty();
        }
        boolean cityScoped = cityId != null && !cityId.isBlank();
        String sql = """
                SELECT rs.direction          AS direction,
                       rs.stop_sequence      AS stop_sequence,
                       rs.distance_from_start_meters AS distance_from_start_meters,
                       bs.id                 AS stop_id,
                       bs.stop_name          AS stop_name,
                       bs.latitude           AS latitude,
                       bs.longitude          AS longitude
                  FROM route_stops rs
                  JOIN bus_stops bs   ON bs.id = rs.stop_id
                  JOIN bus_routes br  ON br.id = rs.route_id
                 WHERE br.route_number = :routeNumber
                """ + (cityScoped ? " AND br.city_id = :cityId\n" : "") + """
                 ORDER BY rs.direction, rs.stop_sequence
                """;
        var spec = databaseClient.sql(sql)
                .bind("routeNumber", routeNumber);
        if (cityScoped) {
            spec = spec.bind("cityId", cityId);
        }
        return spec
                .map((row, meta) -> new RouteStopItem(
                        row.get("stop_id", String.class),
                        row.get("stop_name", String.class),
                        row.get("latitude", Double.class),
                        row.get("longitude", Double.class),
                        row.get("direction", Integer.class),
                        row.get("stop_sequence", Integer.class),
                        row.get("distance_from_start_meters", Double.class)
                ))
                .all();
    }

    private GpsTrailItem trailFor(Vehicle vehicle, List<GpsPoint> points, long cutoffEpochMs) {
        List<TrailPoint> filtered = points.stream()
                .filter(p -> p != null && p.getTime() != null)
                .filter(p -> p.getTime().toInstant(ZoneOffset.UTC).toEpochMilli() >= cutoffEpochMs)
                .map(p -> new TrailPoint(
                        p.getLat(), p.getLon(), p.getSpeed(),
                        p.getTime().toInstant(ZoneOffset.UTC).toEpochMilli()))
                .toList();
        return new GpsTrailItem(
                vehicle.getId() != null ? vehicle.getId().getValue() : null,
                vehicle.getDeviceId(),
                vehicle.getLicensePlate(),
                vehicle.getRouteNumber(),
                filtered
        );
    }

    public record VehicleSnapshotItem(
            String vehicleId,
            String plate,
            String route,
            Double rawLat,
            Double rawLon,
            Double predictedLat,
            Double predictedLon,
            Integer direction,
            Boolean directionConfirmed,
            Double course,
            Double fraction,
            Boolean offRoute,
            Boolean inMotion,
            Double speedKmh,
            Long lastReceivedAtEpochMs
    ) {}

    public record GpsTrailItem(
            String vehicleId,
            String deviceId,
            String plate,
            String route,
            List<TrailPoint> points
    ) {}

    public record TrailPoint(
            Double lat,
            Double lon,
            Double speed,
            Long ts
    ) {}

    public record RouteStopItem(
            String stopId,
            String stopName,
            Double lat,
            Double lon,
            Integer direction,
            Integer sequence,
            Double distanceFromStartMeters
    ) {}

    public record StopItem(
            String stopId,
            String stopName,
            Double lat,
            Double lon
    ) {}

    public record RouteGeometryItem(
            String routeNumber,
            Integer direction,
            JsonNode geometry
    ) {}
}
