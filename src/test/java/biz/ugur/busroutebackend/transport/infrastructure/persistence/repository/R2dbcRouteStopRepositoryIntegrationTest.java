package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcRouteStopRepository.class)
class R2dbcRouteStopRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () -> postgres.getJdbcUrl().replace("jdbc:", "r2dbc:"));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    private static final String ROUTE_ID = "route-1";

    private static final String FWD_1 = "fwd-1";
    private static final String FWD_2 = "fwd-2";
    private static final String FWD_3 = "fwd-3";
    private static final String BWD_1 = "bwd-1";
    private static final String BWD_2 = "bwd-2";
    private static final String BWD_3 = "bwd-3";

    private static final double VEHICLE_LAT = 38.0000;
    private static final double VEHICLE_LON = 58.0004;

    @Autowired
    private DatabaseClient databaseClient;

    private R2dbcRouteStopRepository repository;

    @BeforeEach
    void setUp() {
        repository = new R2dbcRouteStopRepository(databaseClient);

        databaseClient.sql("DROP TABLE IF EXISTS route_stops CASCADE").then().block();
        databaseClient.sql("DROP TABLE IF EXISTS bus_stops CASCADE").then().block();

        databaseClient.sql("""
                CREATE TABLE bus_stops (
                    id          VARCHAR(36) PRIMARY KEY,
                    stop_name   VARCHAR(200),
                    stop_code   VARCHAR(20),
                    is_major_stop BOOLEAN DEFAULT false,
                    latitude    DOUBLE PRECISION NOT NULL,
                    longitude   DOUBLE PRECISION NOT NULL,
                    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """).then().block();

        databaseClient.sql("""
                CREATE TABLE route_stops (
                    id            VARCHAR(36) PRIMARY KEY,
                    route_id      VARCHAR(36) NOT NULL,
                    stop_id       VARCHAR(36) NOT NULL,
                    direction     INTEGER NOT NULL,
                    stop_sequence INTEGER NOT NULL,
                    estimated_travel_time_minutes INTEGER,
                    distance_from_start_meters INTEGER,
                    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """).then().block();

        insertStop(FWD_1, "Forward 1", 38.0000, 57.9990);
        insertStop(FWD_2, "Forward 2", 38.0000, 58.0000);
        insertStop(FWD_3, "Forward 3", 38.0000, 58.0010);

        insertStop(BWD_1, "Backward 1", 38.0001, 58.0010);
        insertStop(BWD_2, "Backward 2", 38.0001, 58.0000);
        insertStop(BWD_3, "Backward 3", 38.0001, 57.9990);

        insertRouteStop("rs-fwd-1", ROUTE_ID, FWD_1, 0, 1, 0);
        insertRouteStop("rs-fwd-2", ROUTE_ID, FWD_2, 0, 2, 88);
        insertRouteStop("rs-fwd-3", ROUTE_ID, FWD_3, 0, 3, 176);

        insertRouteStop("rs-bwd-1", ROUTE_ID, BWD_1, 1, 1, 0);
        insertRouteStop("rs-bwd-2", ROUTE_ID, BWD_2, 1, 2, 88);
        insertRouteStop("rs-bwd-3", ROUTE_ID, BWD_3, 1, 3, 176);
    }

    private void insertStop(String id, String name, double lat, double lon) {
        databaseClient.sql("INSERT INTO bus_stops (id, stop_name, stop_code, latitude, longitude) VALUES (:id, :name, :code, :lat, :lon)")
                .bind("id", id)
                .bind("name", name)
                .bind("code", id.toUpperCase())
                .bind("lat", lat)
                .bind("lon", lon)
                .then()
                .block();
    }

    private void insertRouteStop(String id, String routeId, String stopId, int direction, int sequence, int distanceFromStart) {
        databaseClient.sql("INSERT INTO route_stops (id, route_id, stop_id, direction, stop_sequence, distance_from_start_meters) VALUES (:id, :rid, :sid, :dir, :seq, :dist)")
                .bind("id", id)
                .bind("rid", routeId)
                .bind("sid", stopId)
                .bind("dir", direction)
                .bind("seq", sequence)
                .bind("dist", distanceFromStart)
                .then()
                .block();
    }

    @Test
    void getRouteStops_returnsForwardStopsInSequenceOrder() {
        StepVerifier.create(repository.getRouteStops(ROUTE_ID, 0).collectList())
                .assertNext(stops -> {
                    assertThat(stops).hasSize(3);
                    assertThat(stops.get(0).getStopId()).isEqualTo(FWD_1);
                    assertThat(stops.get(1).getStopId()).isEqualTo(FWD_2);
                    assertThat(stops.get(2).getStopId()).isEqualTo(FWD_3);
                })
                .verifyComplete();
    }

    @Test
    void getRouteStops_returnsBackwardStopsInSequenceOrder() {
        StepVerifier.create(repository.getRouteStops(ROUTE_ID, 1).collectList())
                .assertNext(stops -> {
                    assertThat(stops).hasSize(3);
                    assertThat(stops.get(0).getStopId()).isEqualTo(BWD_1);
                    assertThat(stops.get(1).getStopId()).isEqualTo(BWD_2);
                    assertThat(stops.get(2).getStopId()).isEqualTo(BWD_3);
                })
                .verifyComplete();
    }

    @Test
    void findNearestStopSequence_picksClosestStopWithoutDirectionHint() {
        StepVerifier.create(repository.findNearestStopSequence(ROUTE_ID, VEHICLE_LAT, VEHICLE_LON, null))
                .assertNext(result -> {
                    assertThat(result.sequence()).isEqualTo(2);
                    assertThat(result.direction()).isEqualTo(0);
                })
                .verifyComplete();
    }

    @Test
    void findDirectionByCourse_clearForwardCoursePicksDirection0() {
        Double clearForwardCourse = 80.0;

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, VEHICLE_LAT, VEHICLE_LON, clearForwardCourse, null))
                .assertNext(result -> {
                    assertThat(result.direction()).isEqualTo(0);
                    assertThat(result.sequence()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void findDirectionByCourse_clearBackwardCoursePicksDirection1() {
        Double clearBackwardCourse = 280.0;

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, VEHICLE_LAT, VEHICLE_LON, clearBackwardCourse, null))
                .assertNext(result -> {
                    assertThat(result.direction()).isEqualTo(1);
                    assertThat(result.sequence()).isEqualTo(2);
                })
                .verifyComplete();
    }

    @Test
    void findDirectionByCourse_ambiguousCourseKeepsForwardWhenCurrentDirectionIs0() {
        Double ambiguousCourse = 10.0;

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, VEHICLE_LAT, VEHICLE_LON, ambiguousCourse, 0))
                .assertNext(result -> assertThat(result.direction()).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void findDirectionByCourse_ambiguousCourseKeepsBackwardWhenCurrentDirectionIs1() {
        Double ambiguousCourse = 10.0;

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, VEHICLE_LAT, VEHICLE_LON, ambiguousCourse, 1))
                .assertNext(result -> assertThat(result.direction()).isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void findDirectionByCourse_unknownCurrentDirectionFallsBackToBearingComparison() {
        Double clearForwardCourse = 90.0;

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, VEHICLE_LAT, VEHICLE_LON, clearForwardCourse, null))
                .assertNext(result -> assertThat(result.direction()).isEqualTo(0))
                .verifyComplete();
    }

    @Test
    void findDirectionByCourse_returnsEmptyForNullInputs() {
        StepVerifier.create(repository.findDirectionByCourse(null, VEHICLE_LAT, VEHICLE_LON, 90.0, 0))
                .verifyComplete();

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, null, VEHICLE_LON, 90.0, 0))
                .verifyComplete();

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, VEHICLE_LAT, null, 90.0, 0))
                .verifyComplete();

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, VEHICLE_LAT, VEHICLE_LON, null, 0))
                .verifyComplete();
    }
}
