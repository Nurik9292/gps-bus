package biz.ugur.busroutebackend.transport.infrastructure.monitoring.presence;

import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.GpsAlertProperties;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteAssignmentRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.OffRouteRecord;
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.OffRouteStateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FleetPresenceAlertMonitorTest {

    private EmailNotificationService email;
    private RouteAssignmentRepository assignments;
    private VehicleRepository vehicles;
    private BusRouteRepository busRoutes;
    private OffRouteStateRegistry registry;
    private GpsAlertProperties gpsProps;
    private FleetPresenceAlertProperties props;
    private FleetPresenceAlertMonitor monitor;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-09T10:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        email = mock(EmailNotificationService.class);
        when(email.sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        assignments = mock(RouteAssignmentRepository.class);
        vehicles = mock(VehicleRepository.class);
        registry = new OffRouteStateRegistry();
        gpsProps = new GpsAlertProperties();
        gpsProps.setRecipients("ops@busroute.tm");
        props = new FleetPresenceAlertProperties();

        busRoutes = mock(BusRouteRepository.class);
        when(busRoutes.findActiveRoutes()).thenReturn(Flux.empty());
        when(vehicles.findActiveVehicles()).thenReturn(Flux.empty());
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FULL_DAY))).thenReturn(Flux.empty());
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.SECOND))).thenReturn(Flux.empty());
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST))).thenReturn(Flux.empty());

        monitor = new FleetPresenceAlertMonitor(email, new biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours(new biz.ugur.busroutebackend.shared.infrastructure.email.MailProperties(), java.time.Clock.fixed(java.time.Instant.parse("2026-05-12T10:00:00Z"), java.time.ZoneOffset.UTC)), props, gpsProps, assignments, vehicles, busRoutes, registry, clock);
    }

    private RouteAssignment assignment(String vehicleId) {
        RouteAssignment a = mock(RouteAssignment.class);
        when(a.isCurrentlyValid()).thenReturn(true);
        when(a.getVehicleId()).thenReturn(VehicleId.of(vehicleId));
        return a;
    }

    private Vehicle silentVehicle(String vehicleId) {
        Vehicle v = mock(Vehicle.class);
        when(v.getIsActive()).thenReturn(true);
        when(v.getLicensePlate()).thenReturn("AG-" + vehicleId);
        when(v.getRouteNumber()).thenReturn("12");
        when(v.getLastPositionUpdate()).thenReturn(LocalDateTime.of(2026, 6, 9, 9, 20));
        return v;
    }

    @Test
    void sendsSummaryWhenAssignedVehicleWentSilent() {
        RouteAssignment a = assignment("v1");
        Vehicle v = silentVehicle("v1");
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST)))
                .thenReturn(Flux.just(a));
        when(vehicles.findById(VehicleId.of("v1"))).thenReturn(Mono.just(v));

        StepVerifier.create(monitor.checkNow()).verifyComplete();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).sendGpsAlert(eq(List.of("ops@busroute.tm")), anyString(),
                eq(AlertKind.ASSIGNED_NOT_ON_LINE), anyString(), body.capture());
    }

    @Test
    void doesNotSendWhenAllOnline() {
        Vehicle online = mock(Vehicle.class);
        when(online.getIsActive()).thenReturn(true);
        when(online.getLastPositionUpdate()).thenReturn(LocalDateTime.of(2026, 6, 9, 9, 59));
        RouteAssignment a = assignment("v1");
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST)))
                .thenReturn(Flux.just(a));
        when(vehicles.findById(VehicleId.of("v1"))).thenReturn(Mono.just(online));

        StepVerifier.create(monitor.checkNow()).verifyComplete();

        verify(email, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void sendsAlertWhenAssignedVehicleIsOffRoute() {
        RouteAssignment a = assignment("v1");
        Vehicle v = mock(Vehicle.class);
        when(v.getIsActive()).thenReturn(true);
        when(v.getLicensePlate()).thenReturn("AG-v1");
        when(v.getRouteNumber()).thenReturn("12");
        when(v.getLastPositionUpdate()).thenReturn(LocalDateTime.of(2026, 6, 9, 9, 59));
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST)))
                .thenReturn(Flux.just(a));
        when(vehicles.findById(VehicleId.of("v1"))).thenReturn(Mono.just(v));
        registry.record("v1", LocalDate.of(2026, 6, 9), ShiftType.FIRST,
                new OffRouteRecord(Instant.parse("2026-06-09T09:40:00Z"), 280.0, 37.9, 58.3));

        StepVerifier.create(monitor.checkNow()).verifyComplete();

        verify(email).sendGpsAlert(eq(List.of("ops@busroute.tm")), anyString(),
                eq(AlertKind.ASSIGNED_NOT_ON_LINE), anyString(), anyString());
    }

    @Test
    void doesNotResendSameSetWithinCooldown() {
        RouteAssignment a = assignment("v1");
        Vehicle v = silentVehicle("v1");
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST)))
                .thenReturn(Flux.just(a));
        when(vehicles.findById(VehicleId.of("v1"))).thenReturn(Mono.just(v));

        StepVerifier.create(monitor.checkNow()).verifyComplete();
        StepVerifier.create(monitor.checkNow()).verifyComplete();

        verify(email, times(1)).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void doesNotResendChangedSetWithinCooldown() {
        RouteAssignment a1 = assignment("v1");
        Vehicle v1 = silentVehicle("v1");
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST)))
                .thenReturn(Flux.just(a1));
        when(vehicles.findById(VehicleId.of("v1"))).thenReturn(Mono.just(v1));
        StepVerifier.create(monitor.checkNow()).verifyComplete();

        RouteAssignment a2 = assignment("v2");
        Vehicle v2 = silentVehicle("v2");
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST)))
                .thenReturn(Flux.just(a1, a2));
        when(vehicles.findById(VehicleId.of("v2"))).thenReturn(Mono.just(v2));
        StepVerifier.create(monitor.checkNow()).verifyComplete();

        verify(email, times(1)).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    private BusRoute route(String id, String number) {
        BusRoute r = mock(BusRoute.class);
        when(r.getId()).thenReturn(biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId.of(id));
        when(r.getRouteNumber()).thenReturn(number);
        return r;
    }

    private Vehicle activeVehicle(String id, String plate, String routeNumber, LocalDateTime lastUpdate) {
        Vehicle v = mock(Vehicle.class);
        when(v.getId()).thenReturn(VehicleId.of(id));
        when(v.getIsActive()).thenReturn(true);
        when(v.getLicensePlate()).thenReturn(plate);
        when(v.getRouteNumber()).thenReturn(routeNumber);
        when(v.getAssignedRouteId()).thenReturn(null);
        when(v.getLastPositionUpdate()).thenReturn(lastUpdate);
        return v;
    }

    @Test
    void sendsSummaryWhenRouteHasNoLiveBus() {
        BusRoute r1 = route("r1", "12");
        when(busRoutes.findActiveRoutes()).thenReturn(Flux.just(r1));

        StepVerifier.create(monitor.checkNow()).verifyComplete();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).sendGpsAlert(eq(List.of("ops@busroute.tm")), anyString(),
                eq(AlertKind.ASSIGNED_NOT_ON_LINE), anyString(), body.capture());
        assertThat(body.getValue()).contains("не назначен");
    }

    @Test
    void sendsSummaryWhenActiveVehicleHasNoAssignment() {
        Vehicle v9 = activeVehicle("v9", "AG-9", "7", LocalDateTime.of(2026, 6, 9, 9, 59));
        when(vehicles.findActiveVehicles()).thenReturn(Flux.just(v9));

        StepVerifier.create(monitor.checkNow()).verifyComplete();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).sendGpsAlert(anyList(), anyString(), eq(AlertKind.ASSIGNED_NOT_ON_LINE),
                anyString(), body.capture());
        assertThat(body.getValue()).contains("AG-9");
    }

    @Test
    void capsRowsAndShowsOverflowCount() {
        props.setMaxRowsPerSection(2);
        BusRoute r1 = route("r1", "1");
        BusRoute r2 = route("r2", "2");
        BusRoute r3 = route("r3", "3");
        when(busRoutes.findActiveRoutes()).thenReturn(Flux.just(r1, r2, r3));

        StepVerifier.create(monitor.checkNow()).verifyComplete();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(email).sendGpsAlert(anyList(), anyString(), any(), anyString(), body.capture());
        assertThat(body.getValue()).contains("… и ещё 1");
    }

    @Test
    void doesNotResendSameEmptyRouteSetWithinCooldown() {
        BusRoute r1 = route("r1", "12");
        when(busRoutes.findActiveRoutes()).thenReturn(Flux.just(r1));

        StepVerifier.create(monitor.checkNow()).verifyComplete();
        StepVerifier.create(monitor.checkNow()).verifyComplete();

        verify(email, times(1)).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }
}
