package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteSwapGeoDictionaryTest {

    private static final double BASE_LAT = 37.9500;
    private static final double BASE_LON = 58.3000;
    private static final double LON_DEGREES_PER_KM = 1.0 / (111.320 * Math.cos(Math.toRadians(BASE_LAT)));
    private static final double LAT_DEGREES_PER_KM = 1.0 / 110.540;

    @Mock
    private BusRouteRepository busRouteRepository;

    private RouteSwapProperties properties;
    private RouteSwapGeoDictionary dictionary;

    @BeforeEach
    void setUp() {
        properties = new RouteSwapProperties();
        dictionary = new RouteSwapGeoDictionary(busRouteRepository, properties);
    }

    private static String eastWestWkt(double startLat, double startLon, double lengthKm, int points) {
        return "LINESTRING(" + IntStream.range(0, points)
                .mapToObj(i -> {
                    double lon = startLon + lengthKm * LON_DEGREES_PER_KM * i / (points - 1);
                    return String.format(Locale.ROOT, "%.6f %.6f", lon, startLat);
                })
                .collect(Collectors.joining(", ")) + ")";
    }

    private static String halfSharedThenNorthWkt(double startLat, double startLon, double lengthKm, int points) {
        return "LINESTRING(" + IntStream.range(0, points)
                .mapToObj(i -> {
                    double progressKm = lengthKm * i / (points - 1);
                    double lon;
                    double lat;
                    if (progressKm <= lengthKm / 2) {
                        lon = startLon + progressKm * LON_DEGREES_PER_KM;
                        lat = startLat;
                    } else {
                        lon = startLon + (lengthKm / 2) * LON_DEGREES_PER_KM;
                        lat = startLat + (progressKm - lengthKm / 2) * LAT_DEGREES_PER_KM;
                    }
                    return String.format(Locale.ROOT, "%.6f %.6f", lon, lat);
                })
                .collect(Collectors.joining(", ")) + ")";
    }

    private static BusRoute route(String id, String number, String wkt) {
        return BusRoute.builder()
                .id(new BusRouteId(id))
                .routeNumber(number)
                .isActive(true)
                .cityId("city-001")
                .routeGeometryForward(wkt)
                .routeGeometryBackward(wkt)
                .build();
    }

    private RouteSwapGeoDictionary.Snapshot buildSnapshot() {
        String longAxis = eastWestWkt(BASE_LAT, BASE_LON, 10.0, 101);
        when(busRouteRepository.findActiveRoutes()).thenReturn(Flux.just(
                route("axis-a", "61", longAxis),
                route("axis-b", "80", longAxis),
                route("axis-p", "27", halfSharedThenNorthWkt(BASE_LAT, BASE_LON, 10.0, 101)),
                route("axis-c", "39", eastWestWkt(BASE_LAT + 5.0 * LAT_DEGREES_PER_KM, BASE_LON, 10.0, 101))
        ));
        StepVerifier.create(dictionary.snapshot()).expectNextCount(1).verifyComplete();
        return dictionary.current();
    }

    @Test
    void fullTwinsShareFamilyAndPartialOverlapDoesNot() {
        RouteSwapGeoDictionary.Snapshot snapshot = buildSnapshot();

        assertEquals(snapshot.familyKeyOf("axis-a"), snapshot.familyKeyOf("axis-b"));
        assertNotEquals(snapshot.familyKeyOf("axis-a"), snapshot.familyKeyOf("axis-p"));
        assertNotEquals(snapshot.familyKeyOf("axis-a"), snapshot.familyKeyOf("axis-c"));
        assertTrue(snapshot.familyLabel(snapshot.familyKeyOf("axis-a")).contains("61"));
        assertTrue(snapshot.familyLabel(snapshot.familyKeyOf("axis-a")).contains("80"));
        assertNull(snapshot.familyKeyOf("unknown-axis"));
    }

    @Test
    void familyDistanceIsSmallOnAxisAndLargeOnRemoteAxis() {
        RouteSwapGeoDictionary.Snapshot snapshot = buildSnapshot();

        double onAxis = snapshot.familyDistanceMeters("axis-a", BASE_LAT, BASE_LON + 3.0 * LON_DEGREES_PER_KM);
        double remote = snapshot.familyDistanceMeters("axis-a", BASE_LAT + 5.0 * LAT_DEGREES_PER_KM, BASE_LON + 3.0 * LON_DEGREES_PER_KM);

        assertTrue(onAxis < 40.0, "expected on-axis distance < 40m, got " + onAxis);
        assertTrue(remote > 4500.0 && remote < 5500.0, "expected ~5km, got " + remote);
    }

    @Test
    void bestForeignOnRemoteAxisIsUniqueVisit() {
        RouteSwapGeoDictionary.Snapshot snapshot = buildSnapshot();

        Optional<RouteSwapGeoDictionary.ForeignMatch> match = snapshot.bestForeign(
                "axis-a", BASE_LAT + 5.0 * LAT_DEGREES_PER_KM, BASE_LON + 3.0 * LON_DEGREES_PER_KM);

        assertTrue(match.isPresent());
        assertEquals(snapshot.familyKeyOf("axis-c"), match.get().familyKey());
        assertTrue(match.get().distanceMeters() < 40.0);
        assertTrue(match.get().uniqueVisit());
    }

    @Test
    void bestForeignOnSharedCorridorIsNotUnique() {
        RouteSwapGeoDictionary.Snapshot snapshot = buildSnapshot();

        Optional<RouteSwapGeoDictionary.ForeignMatch> match = snapshot.bestForeign(
                "axis-c", BASE_LAT, BASE_LON + 2.0 * LON_DEGREES_PER_KM);

        assertTrue(match.isPresent());
        assertTrue(match.get().distanceMeters() < 40.0);
        assertFalse(match.get().uniqueVisit());
    }

    @Test
    void intraFamilyProbeSeesAxisFarButFamilyNear() {
        RouteSwapGeoDictionary.Snapshot snapshot = buildSnapshot();

        double tailLat = BASE_LAT + 4.0 * LAT_DEGREES_PER_KM;
        double tailLon = BASE_LON + 5.0 * LON_DEGREES_PER_KM;
        double axisDistance = snapshot.axisDistanceMeters("axis-a", tailLat, tailLon);
        double familyOfPDistance = snapshot.familyDistanceMeters("axis-p", tailLat, tailLon);

        assertTrue(axisDistance > 3000.0, "tail of P is far from axis A, got " + axisDistance);
        assertTrue(familyOfPDistance < 40.0, "tail belongs to P, got " + familyOfPDistance);
    }

    @Test
    void invalidateForcesRebuildWithFreshData() {
        buildSnapshot();
        when(busRouteRepository.findActiveRoutes()).thenReturn(Flux.just(
                route("axis-only", "5", eastWestWkt(BASE_LAT, BASE_LON, 10.0, 51))));

        dictionary.invalidate();

        StepVerifier.create(dictionary.snapshot()).expectNextCount(1).verifyComplete();
        assertNull(dictionary.current().familyKeyOf("axis-a"));
        assertTrue(dictionary.current().knowsAxis("axis-only"));
    }
}
