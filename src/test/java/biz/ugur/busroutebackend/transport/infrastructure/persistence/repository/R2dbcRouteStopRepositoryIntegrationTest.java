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
import org.testcontainers.utility.DockerImageName;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;

@DataR2dbcTest
@Testcontainers
@Import(R2dbcRouteStopRepository.class)
class R2dbcRouteStopRepositoryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine").asCompatibleSubstituteFor("postgres"))
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

        databaseClient.sql("CREATE EXTENSION IF NOT EXISTS postgis").then().block();

        databaseClient.sql("DROP TABLE IF EXISTS route_stops CASCADE").then().block();
        databaseClient.sql("DROP TABLE IF EXISTS bus_stops CASCADE").then().block();
        databaseClient.sql("DROP TABLE IF EXISTS bus_routes CASCADE").then().block();

        databaseClient.sql("""
                CREATE TABLE bus_routes (
                    id                 VARCHAR(36) PRIMARY KEY,
                    geometry_forward   geometry(LineString, 4326),
                    geometry_backward  geometry(LineString, 4326)
                )
                """).then().block();

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

        insertRouteRow(ROUTE_ID, null, null);

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

    private void insertRouteRow(String routeId, String forwardWkt, String backwardWkt) {
        databaseClient.sql("""
                INSERT INTO bus_routes (id, geometry_forward, geometry_backward)
                VALUES (
                    :id,
                    CASE WHEN :fwd IS NULL THEN NULL ELSE ST_GeomFromText(:fwd, 4326) END,
                    CASE WHEN :bwd IS NULL THEN NULL ELSE ST_GeomFromText(:bwd, 4326) END
                )
                """)
                .bind("id", routeId)
                .bind("fwd", forwardWkt == null ? io.r2dbc.spi.Parameters.in(String.class) : io.r2dbc.spi.Parameters.in(forwardWkt))
                .bind("bwd", backwardWkt == null ? io.r2dbc.spi.Parameters.in(String.class) : io.r2dbc.spi.Parameters.in(backwardWkt))
                .then()
                .block();
    }

    private void updateRouteGeometry(String routeId, String forwardWkt, String backwardWkt) {
        databaseClient.sql("""
                UPDATE bus_routes
                   SET geometry_forward  = CASE WHEN :fwd IS NULL THEN NULL ELSE ST_GeomFromText(:fwd, 4326) END,
                       geometry_backward = CASE WHEN :bwd IS NULL THEN NULL ELSE ST_GeomFromText(:bwd, 4326) END
                 WHERE id = :id
                """)
                .bind("id", routeId)
                .bind("fwd", forwardWkt == null ? io.r2dbc.spi.Parameters.in(String.class) : io.r2dbc.spi.Parameters.in(forwardWkt))
                .bind("bwd", backwardWkt == null ? io.r2dbc.spi.Parameters.in(String.class) : io.r2dbc.spi.Parameters.in(backwardWkt))
                .then()
                .block();
    }

    private void replaceRouteStops(int direction, String[][] stops) {
        databaseClient.sql("DELETE FROM route_stops WHERE route_id = :rid AND direction = :dir")
                .bind("rid", ROUTE_ID)
                .bind("dir", direction)
                .then()
                .block();
        for (int i = 0; i < stops.length; i++) {
            insertRouteStop("rs-d" + direction + "-" + (i + 1), ROUTE_ID, stops[i][0], direction, i + 1, i * 100);
        }
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

    @Test
    void findDirectionByCourse_sharedTerminalStopKeepsCurrentDirection() {
        String sharedTermId = "shared-term";
        insertStop(sharedTermId, "Shared terminal", 38.0000, 58.0000);

        replaceRouteStops(0, new String[][]{
                {sharedTermId},
                {FWD_3}
        });
        replaceRouteStops(1, new String[][]{
                {sharedTermId},
                {BWD_3}
        });

        Double ambiguousCourse = 200.0;

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, 38.0000, 58.0000, ambiguousCourse, 0))
                .assertNext(result -> assertThat(result.direction())
                        .as("when nearest forward and nearest backward are the same physical stop, sticky direction must be preserved")
                        .isEqualTo(0))
                .verifyComplete();

        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, 38.0000, 58.0000, ambiguousCourse, 1))
                .assertNext(result -> assertThat(result.direction())
                        .as("same shared terminal, current direction = backward stays backward")
                        .isEqualTo(1))
                .verifyComplete();
    }

    @Test
    void findDirectionByCourse_lateralTieBreakerPicksClosestPolyline() {
        String forwardWkt  = "LINESTRING(57.999 38.0010, 58.000 38.0010, 58.001 38.0010)";
        String backwardWkt = "LINESTRING(57.999 38.0000, 58.000 38.0000, 58.001 38.0000)";
        updateRouteGeometry(ROUTE_ID, forwardWkt, backwardWkt);

        String fwdNear = "fwd-near"; insertStop(fwdNear, "fnear", 38.0010, 58.0000);
        String shared  = "shared-next"; insertStop(shared,  "snext", 38.0005, 58.0010);
        String bwdNear = "bwd-near"; insertStop(bwdNear, "bnear", 38.0000, 58.0000);

        replaceRouteStops(0, new String[][]{{fwdNear}, {shared}});
        replaceRouteStops(1, new String[][]{{bwdNear}, {shared}});

        double busLon = 58.0001;
        Double tieCourse = 90.0;

        double busLatNearForward = 38.00075;
        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, busLatNearForward, busLon, tieCourse, null))
                .assertNext(result -> assertThat(result.direction())
                        .as("frontage-road: bearings tie (next stop shared), bus is laterally closer to forward polyline → 0")
                        .isEqualTo(0))
                .verifyComplete();

        double busLatNearBackward = 38.00025;
        StepVerifier.create(repository.findDirectionByCourse(ROUTE_ID, busLatNearBackward, busLon, tieCourse, null))
                .assertNext(result -> assertThat(result.direction())
                        .as("symmetric case: bus near backward polyline must flip the verdict to 1")
                        .isEqualTo(1))
                .verifyComplete();
    }
}
