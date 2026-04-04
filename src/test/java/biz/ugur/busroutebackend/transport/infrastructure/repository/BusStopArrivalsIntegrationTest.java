package biz.ugur.busroutebackend.transport.infrastructure.repository;

import biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties;
import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.BusStopEntityMapper;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.repository.R2dbcBusStopRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;


@DataR2dbcTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "INTEGRATION_TEST", matches = "true")
@Import({R2dbcBusStopRepository.class, BusStopEntityMapper.class, ETAProperties.class})
class BusStopArrivalsIntegrationTest {

    private static final String TEST_STOP_ID = "test-stop-eta-001";
    private static final double STOP_LAT = 37.895914324968;
    private static final double STOP_LON = 58.380685395682;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> "r2dbc:postgresql://localhost:5432/bus_route_db");
        registry.add("spring.r2dbc.username", () -> "bus_route_user");
        registry.add("spring.r2dbc.password", () -> "bus_route_pass");

        registry.add("app.eta.position.max-age-minutes", () -> 10);
        registry.add("app.eta.position.at-stop-distance-meters", () -> 200);
        registry.add("app.eta.position.max-eta-minutes", () -> 60);
        registry.add("app.eta.speed.moving-threshold-kmh", () -> 5.0);
        registry.add("app.eta.speed.morning-rush-kmh", () -> 12.0);
        registry.add("app.eta.speed.evening-rush-kmh", () -> 12.0);
        registry.add("app.eta.speed.lunch-time-kmh", () -> 18.0);
        registry.add("app.eta.speed.normal-kmh", () -> 25.0);
        registry.add("app.eta.traffic.morning-rush", () -> 1.2);
        registry.add("app.eta.traffic.evening-rush", () -> 1.4);
        registry.add("app.eta.traffic.daytime", () -> 1.1);
        registry.add("app.eta.traffic.evening", () -> 1.1);
        registry.add("app.eta.traffic.night", () -> 0.9);
    }

    @Autowired
    private DatabaseClient databaseClient;

    @Autowired
    private ETAProperties etaProperties;

    private R2dbcBusStopRepository repository;

    @BeforeEach
    void setUp() {
        BusStopEntityMapper mapper = new BusStopEntityMapper();
        repository = new R2dbcBusStopRepository(databaseClient, mapper, etaProperties);

        cleanupTestData();

        insertTestData();
    }

    private void cleanupTestData() {
        databaseClient.sql("DELETE FROM vehicles WHERE id LIKE 'test-v-%'").then().block();
        databaseClient.sql("DELETE FROM route_stops WHERE id LIKE 'test-rs-%'").then().block();
        databaseClient.sql("DELETE FROM bus_stops WHERE id = :id").bind("id", TEST_STOP_ID).then().block();
        databaseClient.sql("DELETE FROM bus_routes WHERE id LIKE 'test-route-%'").then().block();
    }

    private void insertTestData() {
        databaseClient.sql("""
            INSERT INTO bus_stops (id, stop_name, stop_code, latitude, longitude, is_active, city_id)
            VALUES (:id, :name, :code, :lat, :lon, true, 'city-001')
            """)
                .bind("id", TEST_STOP_ID)
                .bind("name", "Test Stop for ETA")
                .bind("code", "TEST001")
                .bind("lat", STOP_LAT)
                .bind("lon", STOP_LON)
                .then().block();

        String routeGeometry = "LINESTRING(58.4100 37.8900, 58.3900 37.8950, 58.3807 37.8959, 58.3700 37.9000, 58.3500 37.9100)";

        databaseClient.sql("""
            INSERT INTO bus_routes (id, route_number, route_name, route_color, is_active,
                                    route_geometry_forward, total_distance_forward_meters)
            VALUES ('test-route-1', '929', 'Test Route 929', '#00796B', true, :geometry, 10000)
            """)
                .bind("geometry", routeGeometry)
                .then().block();

        databaseClient.sql("""
            INSERT INTO bus_routes (id, route_number, route_name, route_color, is_active,
                                    route_geometry_forward, total_distance_forward_meters)
            VALUES ('test-route-2', '914', 'Test Route 914', '#F57C00', true, :geometry, 8000)
            """)
                .bind("geometry", routeGeometry)
                .then().block();

        databaseClient.sql("""
            INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, distance_from_start_meters)
            VALUES ('test-rs-1', 'test-route-1', :stopId, 0, 17, 6800)
            """)
                .bind("stopId", TEST_STOP_ID)
                .then().block();

        databaseClient.sql("""
            INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, distance_from_start_meters)
            VALUES ('test-rs-2', 'test-route-2', :stopId, 0, 15, 5500)
            """)
                .bind("stopId", TEST_STOP_ID)
                .then().block();
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @DisplayName("Should return no arrivals when no vehicles are assigned")
    void shouldReturnNoArrivalsWhenNoVehicles() {
        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals -> assertTrue(arrivals.isEmpty()))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return correct ETA for nearby vehicle (< 300m)")
    void shouldReturnOneMinuteForNearbyVehicle() {
        insertVehicle("test-v-nearby", "1111 AGJ", 37.8945, 58.3808, 30, true,
                     "test-route-1", 0, 15);

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals -> {
                    assertFalse(arrivals.isEmpty(), "Should have arrivals");
                    BusArrivalInfo arrival = arrivals.get(0);
                    assertEquals(1, arrival.getEstimatedArrivalMinutes(),
                            "Nearby vehicle should show 1 minute");
                    assertEquals("at_stop", arrival.getArrivalStatus());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate correct ETA for moving vehicle 2km away")
    void shouldCalculateCorrectETAForMovingVehicle() {
        insertVehicle("test-v-moving", "2222 AGJ", 37.9100, 58.3950, 30, true,
                     "test-route-1", 0, 10);

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals -> {
                    assertFalse(arrivals.isEmpty(), "Should have arrivals");
                    BusArrivalInfo arrival = arrivals.get(0);
                    assertTrue(arrival.getEstimatedArrivalMinutes() >= 2 &&
                              arrival.getEstimatedArrivalMinutes() <= 8,
                            "ETA should be between 2 and 8 minutes, was: " + arrival.getEstimatedArrivalMinutes());
                    assertEquals("approaching", arrival.getArrivalStatus());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should calculate correct ETA for stationary vehicle using default speed")
    void shouldCalculateETAForStationaryVehicle() {
        insertVehicle("test-v-stationary", "3333 AGJ", 37.9200, 58.4000, 0, false,
                     "test-route-1", 0, 8);

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals -> {
                    assertFalse(arrivals.isEmpty(), "Should have arrivals");
                    BusArrivalInfo arrival = arrivals.get(0);
                    assertTrue(arrival.getEstimatedArrivalMinutes() >= 5 &&
                              arrival.getEstimatedArrivalMinutes() <= 15,
                            "ETA should be between 5 and 15 minutes, was: " + arrival.getEstimatedArrivalMinutes());
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should filter out vehicles that already passed the stop")
    void shouldFilterOutVehiclesThatPassedStop() {
        insertVehicle("test-v-passed", "4444 AGJ", 37.8900, 58.3700, 25, true,
                     "test-route-1", 0, 20); 

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals ->
                    assertTrue(arrivals.isEmpty(), "Vehicle that passed stop should be filtered out"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should return vehicles from multiple routes sorted by ETA")
    void shouldReturnVehiclesFromMultipleRoutes() {
        insertVehicle("test-v-route29", "5555 AGJ", 37.9200, 58.4100, 25, true,
                     "test-route-1", 0, 10);

        insertVehicle("test-v-route14", "6666 AGJ", 37.9000, 58.3850, 30, true,
                     "test-route-2", 0, 12);

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals -> {
                    assertEquals(2, arrivals.size(), "Should have 2 arrivals from different routes");

                    List<String> routeNumbers = arrivals.stream()
                            .map(BusArrivalInfo::getRouteNumber)
                            .distinct()
                            .toList();
                    assertEquals(2, routeNumbers.size(), "Should have 2 different routes");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should not return vehicles with old position data")
    void shouldNotReturnVehiclesWithOldPositionData() {
        databaseClient.sql("""
            INSERT INTO vehicles (id, device_id, license_plate, current_latitude, current_longitude,
                                 speed_kmh, is_in_motion, assigned_route_id, current_direction,
                                 last_stop_sequence, is_active, last_position_update)
            VALUES ('test-v-old', 'device-test-v-old', '7777 AGJ', 37.9000, 58.3850, 25, true, 'test-route-1', 0, 10, true,
                    CURRENT_TIMESTAMP - INTERVAL '15 minutes')
            """)
                .then().block();

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals ->
                    assertTrue(arrivals.isEmpty(), "Vehicle with old position should be filtered out"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should filter by direction when vehicle has direction set")
    void shouldFilterByDirection() {
        insertVehicle("test-v-opposite", "8888 AGJ", 37.9100, 58.3900, 30, true,
                     "test-route-1", 1, 10); 

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals ->
                    assertTrue(arrivals.isEmpty(), "Vehicle with wrong direction should be filtered out"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should include vehicle with null direction (not yet determined)")
    void shouldIncludeVehicleWithNullDirection() {
        databaseClient.sql("""
            INSERT INTO vehicles (id, device_id, license_plate, current_latitude, current_longitude,
                                 speed_kmh, is_in_motion, assigned_route_id, current_direction,
                                 last_stop_sequence, is_active, last_position_update)
            VALUES ('test-v-null-dir', 'device-test-v-null-dir', '9999 AGJ', 37.9050, 58.3850, 25, true, 'test-route-1', NULL, 10, true,
                    CURRENT_TIMESTAMP)
            """)
                .then().block();

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals ->
                    assertFalse(arrivals.isEmpty(), "Vehicle with null direction should be included"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Should handle far away vehicles correctly (not show 1 minute)")
    void shouldNotShowOneMinuteForFarVehicles() {
        insertVehicle("test-v-far", "1010 AGJ", 37.9400, 58.4200, 20, true,
                     "test-route-1", 0, 5);

        BusStopId stopId = BusStopId.of(TEST_STOP_ID);

        StepVerifier.create(repository.findArrivingVehicles(stopId, STOP_LAT, STOP_LON).collectList())
                .assertNext(arrivals -> {
                    assertFalse(arrivals.isEmpty(), "Should have arrivals");
                    BusArrivalInfo arrival = arrivals.get(0);
                    assertTrue(arrival.getEstimatedArrivalMinutes() > 5,
                            "Far vehicle should NOT show low ETA. Distance is ~5km, got: " +
                            arrival.getEstimatedArrivalMinutes() + " minutes");
                })
                .verifyComplete();
    }

    private void insertVehicle(String id, String licensePlate, double lat, double lon,
                               double speed, boolean inMotion, String routeId,
                               Integer direction, Integer lastStopSequence) {
        databaseClient.sql("""
            INSERT INTO vehicles (id, device_id, license_plate, current_latitude, current_longitude,
                                 speed_kmh, is_in_motion, assigned_route_id, current_direction,
                                 last_stop_sequence, is_active, last_position_update)
            VALUES (:id, :deviceId, :plate, :lat, :lon, :speed, :motion, :routeId, :direction, :sequence, true,
                    CURRENT_TIMESTAMP)
            """)
                .bind("id", id)
                .bind("deviceId", "device-" + id)
                .bind("plate", licensePlate)
                .bind("lat", lat)
                .bind("lon", lon)
                .bind("speed", speed)
                .bind("motion", inMotion)
                .bind("routeId", routeId)
                .bind("direction", direction)
                .bind("sequence", lastStopSequence)
                .then().block();
    }
}
