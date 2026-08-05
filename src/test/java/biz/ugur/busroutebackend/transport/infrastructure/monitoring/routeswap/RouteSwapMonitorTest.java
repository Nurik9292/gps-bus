package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RouteSwapMonitorTest {

    private static final double BASE_LAT = 37.9500;
    private static final double BASE_LON = 58.3000;
    private static final double LON_DEGREES_PER_KM = 1.0 / (111.320 * Math.cos(Math.toRadians(BASE_LAT)));
    private static final double LAT_DEGREES_PER_KM = 1.0 / 110.540;
    private static final Instant SHIFT_START = Instant.parse("2026-07-30T04:00:00Z");

    @Mock
    private BusRouteRepository busRouteRepository;
    @Mock
    private EmailNotificationService emailService;
    @Mock
    private AlertQuietHours quietHours;
    @Mock
    private biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository auditRepository;

    private RouteSwapProperties properties;
    private RouteSwapGeoDictionary dictionary;
    private RouteSwapMonitor monitor;

    @BeforeEach
    void setUp() {
        properties = new RouteSwapProperties();
        properties.setEnabled(true);
        properties.setMode(RouteSwapProperties.Mode.SHADOW);
        properties.setRecipients("ops@busroute.tm");
        dictionary = new RouteSwapGeoDictionary(busRouteRepository, properties);
        when(busRouteRepository.findActiveRoutes()).thenReturn(Flux.just(
                route("axis-a", "61", axisWkt(0.0, 0.0, 10.0, 101)),
                route("axis-t", "80", axisWkt(0.0, 0.0, 7.0, 71)),
                route("axis-c", "39", axisWkt(5.0, 0.0, 10.0, 101))
        ));
        StepVerifier.create(dictionary.snapshot()).expectNextCount(1).verifyComplete();
        when(quietHours.active()).thenReturn(false);
        when(emailService.sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(auditRepository.tryRecordVerdict(anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString())).thenReturn(Mono.just(true));
        when(auditRepository.logAssignmentChange(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        monitor = new RouteSwapMonitor(properties, dictionary, emailService, quietHours, auditRepository);
    }

    private static String axisWkt(double northOffsetKm, double eastStartKm, double lengthKm, int points) {
        return "LINESTRING(" + IntStream.range(0, points)
                .mapToObj(i -> {
                    double lon = BASE_LON + (eastStartKm + lengthKm * i / (points - 1)) * LON_DEGREES_PER_KM;
                    double lat = BASE_LAT + northOffsetKm * LAT_DEGREES_PER_KM;
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

    private void drive(String assignedRouteId, double northOffsetKm, double fromEastKm, double toEastKm,
                       Instant start, Duration duration) {
        int fixes = (int) (duration.getSeconds() / 30);
        for (int i = 0; i <= fixes; i++) {
            double progress = (double) i / fixes;
            double eastKm = fromEastKm + (toEastKm - fromEastKm) * progress;
            monitor.onFix(new RouteSwapFix(
                    "veh-1", "1903 AGH", assignedRouteId, "100",
                    BASE_LAT + northOffsetKm * LAT_DEGREES_PER_KM,
                    BASE_LON + eastKm * LON_DEGREES_PER_KM,
                    35.0, false,
                    start.plusSeconds(30L * i)));
        }
    }

    @Test
    void vehicleOnAssignedFamilyProducesNoAlert() {
        drive("axis-a", 0.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));

        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void vehicleOnForeignUniqueAxisTriggersSwapDigest() {
        drive("axis-a", 5.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));

        monitor.flushDigest();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendGpsAlert(
                eq(properties.recipientList()), anyString(), eq(AlertKind.ROUTE_SWAP), anyString(), body.capture());
        assertTrue(body.getValue().contains("SWAP_SUSPECTED"), body.getValue());
        assertTrue(body.getValue().contains("1903 AGH"), body.getValue());
        assertTrue(body.getValue().contains("39@city-001"), body.getValue());
    }

    @Test
    void reassignmentGraceSuppressesSwapVerdict() {
        drive("axis-c", 5.0, 0.0, 4.0, SHIFT_START, Duration.ofMinutes(40));
        drive("axis-a", 5.0, 4.0, 10.0, SHIFT_START.plus(Duration.ofMinutes(40)), Duration.ofMinutes(59));

        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void trackFarFromEveryAxisReportsNoAxisFits() {
        drive("axis-a", 2.5, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));

        monitor.flushDigest();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendGpsAlert(
                anyList(), anyString(), eq(AlertKind.ROUTE_SWAP), anyString(), body.capture());
        assertTrue(body.getValue().contains("NO_AXIS_FITS"), body.getValue());
    }

    @Test
    void uniqueTailOfFamilyMemberReportsIntraFamilyMismatch() {
        drive("axis-t", 0.0, 7.5, 10.0, SHIFT_START, Duration.ofMinutes(40));
        drive("axis-t", 0.0, 10.0, 7.5, SHIFT_START.plus(Duration.ofMinutes(40)), Duration.ofMinutes(40));

        monitor.flushDigest();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendGpsAlert(
                anyList(), anyString(), eq(AlertKind.ROUTE_SWAP), anyString(), body.capture());
        assertTrue(body.getValue().contains("INTRA_FAMILY_MISMATCH"), body.getValue());
    }

    @Test
    void repeatedEpisodeInSameShiftIsDeduplicated() {
        drive("axis-a", 5.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));
        monitor.flushDigest();
        drive("axis-a", 5.0, 8.0, 0.0, SHIFT_START.plus(Duration.ofMinutes(81)), Duration.ofMinutes(80));

        monitor.flushDigest();

        verify(emailService, times(1)).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void quietHoursDeferDigestUntilMorningFlush() {
        drive("axis-a", 5.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));
        when(quietHours.active()).thenReturn(true);

        monitor.flushDigest();
        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());

        when(quietHours.active()).thenReturn(false);
        monitor.flushDigest();
        verify(emailService, times(1)).sendGpsAlert(
                anyList(), anyString(), eq(AlertKind.ROUTE_SWAP), anyString(), anyString());
    }

    @Test
    void modeOffSuppressesProcessingAndDigest() {
        properties.setMode(RouteSwapProperties.Mode.OFF);

        drive("axis-a", 5.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));
        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void parkingGapSplitsEpisodeAndSuppressesDeadheadVerdict() {
        drive("axis-a", 2.5, 0.0, 3.0, SHIFT_START, Duration.ofMinutes(35));
        drive("axis-a", 2.5, 3.0, 0.0, SHIFT_START.plus(Duration.ofMinutes(95)), Duration.ofMinutes(35));

        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void swapSupersedesEarlierNoAxisFitsAndBlocksLaterOnes() {
        drive("axis-a", 2.5, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));
        drive("axis-a", 5.0, 8.0, 0.0, SHIFT_START.plus(Duration.ofMinutes(81)), Duration.ofMinutes(80));
        drive("axis-a", 2.5, 0.0, 8.0, SHIFT_START.plus(Duration.ofMinutes(162)), Duration.ofMinutes(80));

        monitor.flushDigest();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendGpsAlert(
                anyList(), anyString(), eq(AlertKind.ROUTE_SWAP), anyString(), body.capture());
        assertTrue(body.getValue().contains("supersedes NO_AXIS_FITS"), body.getValue());
        assertTrue(body.getValue().split("NO_AXIS_FITS", -1).length == 3, body.getValue());
    }

    @Test
    void alreadyPersistedVerdictSkipsDigestAfterRestart() {
        when(auditRepository.tryRecordVerdict(anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString())).thenReturn(Mono.just(false));

        drive("axis-a", 5.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));
        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void assignmentChangeIsLoggedToAudit() {
        drive("axis-c", 5.0, 0.0, 2.0, SHIFT_START, Duration.ofMinutes(20));
        drive("axis-a", 5.0, 2.0, 4.0, SHIFT_START.plus(Duration.ofMinutes(20)), Duration.ofMinutes(20));

        verify(auditRepository).logAssignmentChange("veh-1", "1903 AGH", "axis-c", "axis-a");
    }

    @Test
    void persistFailureIsFailOpenAndStillAlerts() {
        when(auditRepository.tryRecordVerdict(anyString(), anyString(), anyString(), anyString(),
                anyString(), any(), anyString())).thenReturn(Mono.error(new RuntimeException("db down")));

        drive("axis-a", 5.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));
        monitor.flushDigest();

        verify(emailService, times(1)).sendGpsAlert(
                anyList(), anyString(), eq(AlertKind.ROUTE_SWAP), anyString(), anyString());
    }

    @Test
    void warmupSeedsDedupFromPersistedVerdicts() {
        when(auditRepository.findVerdictKeysSince(any())).thenReturn(Flux.just(
                new biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.VerdictKey(
                        "1903 AGH", java.time.LocalDate.of(2026, 7, 30), "FIRST", "SWAP_SUSPECTED")));
        monitor.warmupFromPersistedVerdicts();

        drive("axis-a", 5.0, 0.0, 8.0, SHIFT_START, Duration.ofMinutes(80));
        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void outsideShiftHoursNoAxisFitsIsSuppressed() {
        Instant nightStart = Instant.parse("2026-07-30T18:00:00Z");
        drive("axis-a", 2.5, 0.0, 13.0, nightStart, Duration.ofMinutes(130));

        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void noAxisFitsSuppressedNearShiftEnd() {
        properties.setEndOfShiftBufferMinutes(120);
        Instant warmup = Instant.parse("2026-07-30T14:30:00Z");
        drive("axis-a", 0.0, 0.0, 4.0, warmup, Duration.ofMinutes(60));
        drive("axis-a", 2.5, 4.0, 0.0, warmup.plus(Duration.ofMinutes(60)), Duration.ofMinutes(80));

        monitor.flushDigest();

        verify(emailService, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }
}
