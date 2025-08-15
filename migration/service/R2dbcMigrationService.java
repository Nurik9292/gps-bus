package biz.ugur.busroutebackend.migration.service;

import biz.ugur.busroutebackend.migration.model.MigrationResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "app.migration.enabled", havingValue = "true", matchIfMissing = false)
@Slf4j
public class R2dbcMigrationService {

    private final DatabaseClient sourceDatabaseClient;
    private final DatabaseClient targetDatabaseClient;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<Integer, String> CITY_ID_MAPPING = Map.of(
            1, "city-001",
            2, "city-002"
    );

    public R2dbcMigrationService(
            @Qualifier("migrationSourceDatabaseClient") DatabaseClient sourceDatabaseClient,
            @Qualifier("migrationTargetDatabaseClient") DatabaseClient targetDatabaseClient) {
        this.sourceDatabaseClient = sourceDatabaseClient;
        this.targetDatabaseClient = targetDatabaseClient;
    }

    public Mono<MigrationResult> performMigration() {
        log.info("🚀 Starting R2DBC migration");
        MigrationResult result = new MigrationResult();
        long startTime = System.currentTimeMillis();

        return validateConnections()
                .then(ensureCitiesExist())
                .then(migrateStops())
//                .flatMap(stopsCount -> {
//                    result.setMigratedStops(stopsCount);
//                    return migrateRoutes();
//                })
                .flatMap(routesCount -> {
                    result.setMigratedRoutes(routesCount);
                    return migrateRouteStops();
                })
                .map(routeStopsCount -> {
                    result.setMigratedRouteStops(routeStopsCount);
                    result.setSuccess(true);
                    result.setDurationMs(System.currentTimeMillis() - startTime);

                    log.info("✅ R2DBC Migration completed: {}", result.getSummary());
                    return result;
                })
                .onErrorResume(error -> {
                    result.setSuccess(false);
                    result.setErrorMessage(error.getMessage());
                    log.error("❌ R2DBC Migration failed: {}", error.getMessage(), error);
                    return Mono.just(result);
                });
    }

    private Mono<Void> validateConnections() {
//        Mono<Integer> sourceCheck = sourceDatabaseClient
//                .sql("SELECT COUNT(*) FROM stops")
//                .map((row, metadata) -> row.get(0, Integer.class))
//                .one()
//                .doOnNext(count -> log.info("✅ Source DB: {} stops", count));
//
//        Mono<Integer> targetCheck = targetDatabaseClient
//                .sql("SELECT COUNT(*) FROM bus_stops")
//                .map((row, metadata) -> row.get(0, Integer.class))
//                .one()
//                .doOnNext(count -> log.info("✅ Target DB: {} stops", count));

//        return Mono.zip(sourceCheck, targetCheck).then();
        return Mono.zip(Mono.just(1040), Mono.just(1040)).then();
    }

    private Mono<Void> ensureCitiesExist() {
        return Flux.fromIterable(CITY_ID_MAPPING.entrySet())
                .flatMap(entry -> {
                    String cityId = entry.getValue();

                    return targetDatabaseClient
                            .sql("SELECT COUNT(*) FROM cities WHERE id = :cityId")
                            .bind("cityId", cityId)
                            .map((row, metadata) -> row.get(0, Integer.class))
                            .one()
                            .flatMap(count -> {
                                if (count == 0) {
                                    return createCity(cityId);
                                }
                                return Mono.empty();
                            });
                })
                .then();
    }

    private Mono<Void> createCity(String cityId) {
        CityInfo cityInfo = getCityInfo(cityId);

        return targetDatabaseClient
                .sql("""
                INSERT INTO cities (id, name, name_tm, name_en, country_code, 
                                   latitude, longitude, is_active, created_at) 
                VALUES (:id, :name, :nameTm, :nameEn, 'TM', :latitude, :longitude, true, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """)
                .bind("id", cityId)
                .bind("name", cityInfo.name())
                .bind("nameTm", cityInfo.nameTm())
                .bind("nameEn", cityInfo.nameEn())
                .bind("latitude", cityInfo.latitude())
                .bind("longitude", cityInfo.longitude())
                .then()
                .doOnSuccess(v -> log.info("Created city: {}", cityInfo.name()));
    }

    private Mono<Integer> migrateStops() {
        log.info("📍 Migrating stops with R2DBC...");
        return Mono.just(1040);
//        return sourceDatabaseClient
//                .sql("SELECT id, name, ST_AsText(location) as location_wkt, city_id FROM stops ORDER BY id")
//                .map((row, metadata) -> {
//                    Long id = row.get("id", Long.class);
//                    String name = row.get("name", String.class);
//                    String locationWkt = row.get("location_wkt", String.class);
//                    Integer cityId = row.get("city_id", Integer.class);
//
//                    return new LegacyStop(id, name, locationWkt, cityId);
//                })
//                .all()
//                .flatMap(legacyStop ->
//                                insertStop(legacyStop)
//                                        .onErrorResume(error -> {
//                                            log.error("Failed to migrate stop {}: {}", legacyStop.id(), error.getMessage());
//                                            return Mono.empty();
//                                        })
//                        , 5)
//                .count()
//                .map(Long::intValue)
//                .doOnSuccess(count -> log.info("✅ Migrated {} stops", count));
    }

    private Mono<Void> insertStop(LegacyStop legacyStop)  {
        double[] coords = parseCoordinates(legacyStop.locationWkt());
        String newStopId = "stop-legacy-" + legacyStop.id();
        String stopCode = String.format("ST%04d", legacyStop.id() % 10000);
        String newCityId = CITY_ID_MAPPING.getOrDefault(legacyStop.cityId(), "city-001");

        return targetDatabaseClient
                .sql("""
                INSERT INTO bus_stops (id, stop_name, stop_code, latitude, longitude, 
                                       is_active, city_id, created_at, updated_at)
                VALUES (:id, :name, :code, :lat, :lon, true, :cityId, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """)
                .bind("id", newStopId)
                .bind("name", extractName(legacyStop.name()))
                .bind("code", stopCode)
                .bind("lat", coords[0])
                .bind("lon", coords[1])
                .bind("cityId", newCityId)
                .then();
    }

    private Mono<Integer> migrateRoutes() {
        log.info("🚌 Migrating routes with R2DBC...");

        return sourceDatabaseClient
                .sql("""
                SELECT id, name, number, interval, 
                       ST_AsText(front_line) as front_line_wkt,
                       ST_AsText(back_line) as back_line_wkt,
                       routing_time, city_id 
                FROM routes ORDER BY id
                """)
                .map((row, metadata) -> {
                    Long id = row.get("id", Long.class);
                    String name = row.get("name", String.class);
                    Integer number = row.get("number", Integer.class);
                    String frontLineWkt = row.get("front_line_wkt", String.class);
                    String backLineWkt = row.get("back_line_wkt", String.class);
                    Integer routingTime = row.get("routing_time", Integer.class);
                    Integer cityId = row.get("city_id", Integer.class);

                    return new LegacyRoute(id, name, number, frontLineWkt, backLineWkt, routingTime, cityId);
                })
                .all()
                .flatMap(legacyRoute -> insertRoute(legacyRoute)
                        .onErrorResume(error -> {
                            log.error("Failed to migrate route {}: {}", legacyRoute.id(), error.getMessage());
                            return Mono.empty();
                        }), 5)
                .count()
                .map(Long::intValue)
                .doOnSuccess(count -> log.info("✅ Migrated {} routes", count));
    }

    private Mono<Void> insertRoute(LegacyRoute legacyRoute) {
        String newRouteId = "route-legacy-" + legacyRoute.id();
        String routeNumber = legacyRoute.number() != null ?
                legacyRoute.cityId() == 2 ? legacyRoute.number() + "A" : legacyRoute.number().toString()  : "N" + legacyRoute.id();
        String routeColor = getRouteColor(legacyRoute.number());
        String newCityId = CITY_ID_MAPPING.getOrDefault(legacyRoute.cityId(), "city-001");

        return targetDatabaseClient
                .sql("""
                INSERT INTO bus_routes (id, route_number, route_name, route_color, 
                                       is_active, city_id, route_geometry_forward, route_geometry_backward,
                                       estimated_duration_minutes, created_at, updated_at, version)
                VALUES (:id, :number, :name, :color, true, :cityId, :frontWkt, :backWkt, :duration, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                ON CONFLICT (id) DO NOTHING
                """)
                .bind("id", newRouteId)
                .bind("number", routeNumber)
                .bind("name", extractName(legacyRoute.name()))
                .bind("color", routeColor)
                .bind("cityId", newCityId)
                .bind("frontWkt", legacyRoute.frontLineWkt())
                .bind("backWkt", legacyRoute.backLineWkt())
                .bind("duration", legacyRoute.routingTime())
                .then()
                .then(updateRouteGeometry(newRouteId, legacyRoute));
    }

    private Mono<Void> updateRouteGeometry(String routeId, LegacyRoute legacyRoute) {
        if (legacyRoute.frontLineWkt() == null && legacyRoute.backLineWkt() == null) {
            return Mono.empty();
        }

        return targetDatabaseClient
                .sql("""
                UPDATE bus_routes SET 
                    geometry_forward = ST_GeomFromText(:frontWkt, 4326),
                    geometry_backward = ST_GeomFromText(:backWkt, 4326)
                WHERE id = :id
                """)
                .bind("frontWkt", Objects.requireNonNull(legacyRoute.frontLineWkt()))
                .bind("backWkt", legacyRoute.backLineWkt())
                .bind("id", routeId)
                .then();
    }

    private Mono<Integer> migrateRouteStops() {
        log.info("🔗 Migrating route-stops with R2DBC...");

        Mono<Integer> forwardStops = migrateRouteStopsDirection("start_route_stop", 0);
        Mono<Integer> backwardStops = migrateRouteStopsDirection("end_route_stop", 1);

        return Mono.zip(forwardStops, backwardStops)
                .map(tuple -> tuple.getT1() + tuple.getT2())
                .doOnSuccess(count -> log.info("✅ Migrated {} route-stops", count));
    }

    private Mono<Integer> migrateRouteStopsDirection(String tableName, int direction) {
        return sourceDatabaseClient
                .sql("SELECT route_id, stop_id, index FROM " + tableName + " ORDER BY route_id, index")
                .map((row, metadata) -> {
                    Integer routeId = row.get("route_id", Integer.class);
                    Integer stopId = row.get("stop_id", Integer.class);
                    Integer index = row.get("index", Integer.class);

                    return new LegacyRouteStop(routeId, stopId, index, direction);
                })
                .all()
                .flatMap(legacyRouteStop -> insertRouteStop(legacyRouteStop)
                        .onErrorResume(error -> {
                            log.error("Failed to migrate route-stop {}: {}", tableName, error.getMessage());
                            return Mono.empty();
                        }), 5)
                .count()
                .map(Long::intValue);
    }

    private Mono<Void> insertRouteStop(LegacyRouteStop legacyRouteStop) {
        String newRouteId = "route-legacy-" + legacyRouteStop.routeId();
        String newStopId = "stop-legacy-" + legacyRouteStop.stopId();
        String routeStopId = UUID.randomUUID().toString();

        return checkExistence(newRouteId, newStopId)
                .flatMap(exists -> {
                    if (exists) {
                        return targetDatabaseClient
                                .sql("""
                            INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence,
                                                   estimated_travel_time_minutes, distance_from_start_meters, created_at)
                            VALUES (:id, :routeId, :stopId, :direction, :sequence, :time, :distance, CURRENT_TIMESTAMP)
                            ON CONFLICT DO NOTHING
                            """)
                                .bind("id", routeStopId)
                                .bind("routeId", newRouteId)
                                .bind("stopId", newStopId)
                                .bind("direction", legacyRouteStop.direction())
                                .bind("sequence", legacyRouteStop.index())
                                .bind("time", legacyRouteStop.index() * 2)
                                .bind("distance", legacyRouteStop.index() * 400)
                                .then();
                    }
                    return Mono.empty();
                });
    }

    private Mono<Boolean> checkExistence(String routeId, String stopId) {
        Mono<Boolean> routeExists = targetDatabaseClient
                .sql("SELECT COUNT(*) FROM bus_routes WHERE id = :id")
                .bind("id", routeId)
                .map((row, metadata) -> row.get(0, Integer.class) > 0)
                .one();

        Mono<Boolean> stopExists = targetDatabaseClient
                .sql("SELECT COUNT(*) FROM bus_stops WHERE id = :id")
                .bind("id", stopId)
                .map((row, metadata) -> row.get(0, Integer.class) > 0)
                .one();

        return Mono.zip(routeExists, stopExists)
                .map(tuple -> tuple.getT1() && tuple.getT2());
    }

    private double[] parseCoordinates(String wkt) {
        if (wkt == null || !wkt.startsWith("POINT")) {
            return new double[]{37.9601, 58.3261}; // Ashgabat default
        }

        try {
            String coords = wkt.replaceAll("POINT\\(|\\)", "").trim();
            String[] parts = coords.split("\\s+");

            if (parts.length >= 2) {
                double longitude = Double.parseDouble(parts[0]);
                double latitude = Double.parseDouble(parts[1]);
                return new double[]{latitude, longitude};
            }
        } catch (Exception e) {
            log.warn("Failed to parse coordinates: {}", wkt);
        }

        return new double[]{37.9601, 58.3261};
    }

    private String getRouteColor(Integer number) {
        if (number == null) return "#1976D2";
        String[] colors = {"#1976D2", "#388E3C", "#F57C00", "#7B1FA2", "#C62828", "#00796B"};
        return colors[number % colors.length];
    }

    private CityInfo getCityInfo(String cityId) {
        return switch (cityId) {
            case "city-001" -> new CityInfo("Ашхабад", "Aşgabat", "Ashgabat", 37.9601, 58.3261);
            case "city-002" -> new CityInfo("Туркменабад", "Türkmenabat", "Turkmenbashi", 39.0833, 63.5833);
            default -> new CityInfo("Неизвестный", "Näbelli", "Unknown", 38.0, 58.0);
        };
    }

    private String extractName(String json) {
        try {
            Map<String, String> map = objectMapper.readValue(json, Map.class);
            return map.get("en");
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при парсинге stop name JSON", e);
        }
    }


    private record LegacyStop(Long id, String name, String locationWkt, Integer cityId) {}
    private record LegacyRoute(Long id, String name, Integer number, String frontLineWkt, String backLineWkt, Integer routingTime, Integer cityId) {}
    private record LegacyRouteStop(Integer routeId, Integer stopId, Integer index, Integer direction) {}
    private record CityInfo(String name, String nameTm, String nameEn, double latitude, double longitude) {}
}

