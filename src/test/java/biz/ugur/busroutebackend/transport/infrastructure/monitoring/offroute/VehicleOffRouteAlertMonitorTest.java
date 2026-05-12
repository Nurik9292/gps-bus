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
                routeAssignmentRepository, vehicleRepository, busRouteRepository);

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

        verify(emailService, times(1)).sendGpsAlert(
                eq(java.util.List.of("ops@example.com")),
                eq(VEHICLE_ID),
                eq(AlertKind.VEHICLE_OFF_ROUTE),
                anyString(),
                anyString());
    }

    private void awaitAsyncDispatch() throws InterruptedException {
        Thread.sleep(500);
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
}
