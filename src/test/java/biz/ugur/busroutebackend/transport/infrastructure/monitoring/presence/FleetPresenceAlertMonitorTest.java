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
import biz.ugur.busroutebackend.transport.infrastructure.monitoring.offroute.OffRouteStateRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FleetPresenceAlertMonitorTest {

    private EmailNotificationService email;
    private RouteAssignmentRepository assignments;
    private VehicleRepository vehicles;
    private BusRouteRepository routes;
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
        routes = mock(BusRouteRepository.class);
        registry = new OffRouteStateRegistry();
        gpsProps = new GpsAlertProperties();
        gpsProps.setRecipients("ops@busroute.tm");
        props = new FleetPresenceAlertProperties();

        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FULL_DAY))).thenReturn(Flux.empty());

        monitor = new FleetPresenceAlertMonitor(email, props, gpsProps, assignments, vehicles, routes, registry, clock);
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

        monitor.checkNow().block();

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

        monitor.checkNow().block();

        verify(email, never()).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }

    @Test
    void doesNotResendSameSetWithinCooldown() {
        RouteAssignment a = assignment("v1");
        Vehicle v = silentVehicle("v1");
        when(assignments.findActiveByDateAndShift(any(), eq(ShiftType.FIRST)))
                .thenReturn(Flux.just(a));
        when(vehicles.findById(VehicleId.of("v1"))).thenReturn(Mono.just(v));

        monitor.checkNow().block();
        monitor.checkNow().block();

        verify(email, times(1)).sendGpsAlert(anyList(), anyString(), any(), anyString(), anyString());
    }
}
