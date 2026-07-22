package biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneOffset;

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
class VehicleOffRouteAlertMonitorTest {

    @Mock
    private EmailNotificationService emailService;
    @Mock
    private RouteAssignmentRepository routeAssignmentRepository;
    @Mock
    private VehicleRepository vehicleRepository;
    @Mock
    private BusRouteRepository busRouteRepository;

    private OffRouteAlertProperties properties;
    private VehicleOffRouteAlertMonitor monitor;
    private Clock clock;

    private static final Instant T0 = Instant.parse("2026-05-12T10:00:00Z");
    private static final String VEHICLE_ID = "V-001";
    private static final String LICENSE = "AG1234AG";
    private static final String ROUTE_ID = "R-29";
    private static final String ROUTE_NUMBER = "29";

    @BeforeEach
    void setUp() {
        properties = new OffRouteAlertProperties();
        properties.setEnabled(true);
        properties.setRecipients("ops@example.com");
        properties.setEndOfShiftBufferMinutes(15);
        properties.setMinOnRouteSeconds(60);

        clock = Clock.fixed(T0, ZoneOffset.UTC);

        monitor = new VehicleOffRouteAlertMonitor(
                emailService, properties, clock,
                routeAssignmentRepository, vehicleRepository, busRouteRepository,
                new OffRouteStateRegistry());

        when(emailService.sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        Vehicle vehicle = mockVehicle();
        BusRoute busRoute = mockBusRoute();
        when(vehicleRepository.findById(any())).thenReturn(Mono.just(vehicle));
        when(busRouteRepository.findById(any())).thenReturn(Mono.just(busRoute));
    }

    @Test
    void sendsAlertWhenAllConditionsMet() throws InterruptedException {
        RouteAssignment assignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(assignment));

        VehiclePredictionState state = stateOnRouteFor(Duration.ofMinutes(30));
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        monitor.flushDigest();
        verify(emailService, times(1)).sendGpsAlert(
                eq(java.util.List.of("ops@example.com")),
                eq("off-route-digest"),
                eq(AlertKind.VEHICLE_OFF_ROUTE),
                anyString(),
                anyString());
    }

    private void awaitAsyncDispatch() throws InterruptedException {
        Thread.sleep(500);
    }

    @org.junit.jupiter.api.Test
    void digestBatchesMultipleEpisodesIntoOneEmail() throws InterruptedException {
        RouteAssignment assignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(assignment));

        monitor.onWentOffRoute(VEHICLE_ID, stateOnRouteFor(Duration.ofMinutes(30)), 39.95, 58.38, 250.0);
        monitor.onWentOffRoute("V-002", stateOnRouteFor(Duration.ofMinutes(30)), 39.96, 58.39, 400.0);
        awaitAsyncDispatch();
        monitor.flushDigest();

        org.mockito.ArgumentCaptor<String> subject = org.mockito.ArgumentCaptor.forClass(String.class);
        monitor.flushDigest();
        verify(emailService, times(1)).sendGpsAlert(
                eq(java.util.List.of("ops@example.com")),
                eq("off-route-digest"),
                eq(AlertKind.VEHICLE_OFF_ROUTE),
                subject.capture(),
                anyString());
        org.assertj.core.api.Assertions.assertThat(subject.getValue()).contains("2");
    }

    @org.junit.jupiter.api.Test
    void emptyDigestBufferSendsNothing() {
        monitor.flushDigest();
        verify(emailService, org.mockito.Mockito.never())
                .sendGpsAlert(any(), any(), any(), any(), any());
    }

    private VehiclePredictionState stateOnRouteFor(Duration onRouteDuration) {
        Instant firstOnRoute = T0.minus(onRouteDuration);
        return VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(LICENSE)
                .routeNumber(ROUTE_NUMBER)
                .offRoute(true)
                .firstOnRouteAtCurrentShift(firstOnRoute)
                .lastOnRouteAt(T0.minus(Duration.ofSeconds(10)))
                .lastRawToSnapDistanceMeters(250.0)
                .build();
    }

    private RouteAssignment mockAssignmentForFirstShift() {
        RouteAssignment a = org.mockito.Mockito.mock(RouteAssignment.class);
        when(a.getVehicleId()).thenReturn(VehicleId.of(VEHICLE_ID));
        when(a.getRouteId()).thenReturn(BusRouteId.of(ROUTE_ID));
        when(a.getStartTime()).thenReturn(LocalTime.of(7, 0));
        when(a.getEndTime()).thenReturn(LocalTime.of(14, 0));
        when(a.shouldBeActiveAt(any(LocalTime.class))).thenReturn(true);
        return a;
    }

    private Vehicle mockVehicle() {
        Vehicle v = org.mockito.Mockito.mock(Vehicle.class);
        when(v.getLicensePlate()).thenReturn(LICENSE);
        return v;
    }

    private BusRoute mockBusRoute() {
        BusRoute r = org.mockito.Mockito.mock(BusRoute.class);
        when(r.getRouteNumber()).thenReturn(ROUTE_NUMBER);
        return r;
    }

    @Test
    void skipsWhenNoAssignmentForCurrentShift() throws InterruptedException {
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.empty());

        VehiclePredictionState state = stateOnRouteFor(Duration.ofMinutes(30));
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        verify(emailService, org.mockito.Mockito.never()).sendGpsAlert(any(), any(), any(), any(), any());
    }

    @Test
    void skipsWhenAssignmentNotActiveAtNow() throws InterruptedException {
        RouteAssignment assignment = mockAssignmentForFirstShift();
        when(assignment.shouldBeActiveAt(any(LocalTime.class))).thenReturn(false);
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(assignment));

        VehiclePredictionState state = stateOnRouteFor(Duration.ofMinutes(30));
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        verify(emailService, org.mockito.Mockito.never()).sendGpsAlert(any(), any(), any(), any(), any());
    }

    @Test
    void skipsWhenWithinEndOfShiftBuffer() throws InterruptedException {
        clock = Clock.fixed(Instant.parse("2026-05-12T13:50:00Z"), ZoneOffset.UTC);
        monitor = new VehicleOffRouteAlertMonitor(
                emailService, properties, clock,
                routeAssignmentRepository, vehicleRepository, busRouteRepository,
                new OffRouteStateRegistry());
        when(emailService.sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());

        RouteAssignment assignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(assignment));

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(LICENSE)
                .routeNumber(ROUTE_NUMBER)
                .offRoute(true)
                .firstOnRouteAtCurrentShift(Instant.parse("2026-05-12T11:50:00Z"))
                .lastOnRouteAt(Instant.parse("2026-05-12T13:40:00Z"))
                .lastRawToSnapDistanceMeters(250.0)
                .build();
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        verify(emailService, org.mockito.Mockito.never()).sendGpsAlert(any(), any(), any(), any(), any());
    }

    @Test
    void skipsWhenVehicleNeverWasOnRoute() throws InterruptedException {
        RouteAssignment assignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(assignment));

        VehiclePredictionState state = VehiclePredictionState.builder()
                .vehicleId(VEHICLE_ID)
                .licensePlate(LICENSE)
                .offRoute(true)
                .firstOnRouteAtCurrentShift(null)
                .build();
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        verify(emailService, org.mockito.Mockito.never()).sendGpsAlert(any(), any(), any(), any(), any());
    }

    @Test
    void skipsWhenOnRouteLessThanMinSeconds() throws InterruptedException {
        RouteAssignment assignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(assignment));

        VehiclePredictionState state = stateOnRouteFor(Duration.ofSeconds(30));
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        verify(emailService, org.mockito.Mockito.never()).sendGpsAlert(any(), any(), any(), any(), any());
    }

    @Test
    void cleanupRemovesRecordsOlderThanOneDay() {
        OffRouteAlertKey oldKey = new OffRouteAlertKey("V-OLD",
                java.time.LocalDate.ofInstant(T0, ZoneOffset.UTC).minusDays(3), ShiftType.FIRST);
        OffRouteAlertKey freshKey = new OffRouteAlertKey("V-NEW",
                java.time.LocalDate.ofInstant(T0, ZoneOffset.UTC), ShiftType.FIRST);

        java.lang.reflect.Field f;
        try {
            f = VehicleOffRouteAlertMonitor.class.getDeclaredField("alerted");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<OffRouteAlertKey, Instant> map =
                    (java.util.Map<OffRouteAlertKey, Instant>) f.get(monitor);
            map.put(oldKey, T0.minus(Duration.ofDays(3)));
            map.put(freshKey, T0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        monitor.cleanupOldEntries();

        try {
            f = VehicleOffRouteAlertMonitor.class.getDeclaredField("alerted");
            f.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Map<OffRouteAlertKey, Instant> map =
                    (java.util.Map<OffRouteAlertKey, Instant>) f.get(monitor);
            org.junit.jupiter.api.Assertions.assertFalse(map.containsKey(oldKey));
            org.junit.jupiter.api.Assertions.assertTrue(map.containsKey(freshKey));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void skipsSecondAlertForSameVehicleInSameShift() throws InterruptedException {
        RouteAssignment assignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(assignment));

        VehiclePredictionState state = stateOnRouteFor(Duration.ofMinutes(30));
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        monitor.flushDigest();
        verify(emailService, times(1)).sendGpsAlert(any(), any(), any(), any(), any());
    }

    @Test
    void fallsBackToFullDayShiftWhenPrimaryShiftAssignmentMissing() throws InterruptedException {
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.empty());
        RouteAssignment fullDayAssignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FULL_DAY)))
                .thenReturn(Mono.just(fullDayAssignment));

        VehiclePredictionState state = stateOnRouteFor(Duration.ofMinutes(30));
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();

        monitor.flushDigest();
        verify(emailService, times(1)).sendGpsAlert(
                eq(java.util.List.of("ops@example.com")),
                eq("off-route-digest"),
                eq(AlertKind.VEHICLE_OFF_ROUTE),
                anyString(),
                anyString());
    }

    @Test
    void allowsRetryAfterFilterRejectionInSameShift() throws InterruptedException {
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), any()))
                .thenReturn(Mono.empty());

        VehiclePredictionState state = stateOnRouteFor(Duration.ofMinutes(30));
        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();
        verify(emailService, never()).sendGpsAlert(any(), any(), any(), any(), any());

        org.mockito.Mockito.reset(routeAssignmentRepository);
        RouteAssignment retryAssignment = mockAssignmentForFirstShift();
        when(routeAssignmentRepository.findActiveByVehicleAndDateAndShift(any(), any(), eq(ShiftType.FIRST)))
                .thenReturn(Mono.just(retryAssignment));

        monitor.onWentOffRoute(VEHICLE_ID, state, 39.95, 58.38, 250.0);
        awaitAsyncDispatch();
        monitor.flushDigest();
        verify(emailService, times(1)).sendGpsAlert(any(), any(), any(), any(), any());
    }
}
