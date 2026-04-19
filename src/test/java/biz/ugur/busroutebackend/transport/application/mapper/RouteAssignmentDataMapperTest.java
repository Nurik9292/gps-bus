package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import biz.ugur.busroutebackend.transport.domain.model.RouteAssignment;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class RouteAssignmentDataMapperTest {

    @InjectMocks
    private RouteAssignmentDataMapper mapper;

    @Mock
    private VehicleRepository vehicleRepository;

    @Mock
    private BusRouteRepository busRouteRepository;

    private RouteAssignment assignment;
    private Vehicle vehicle;
    private BusRoute route;

    @BeforeEach
    void setUp() {
        vehicle = Vehicle.create("dev-1", "1234 AGH");
        route = BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);

        assignment = RouteAssignment.create(
                vehicle.getId(),
                route.getId(),
                LocalDate.now().plusDays(1),
                ShiftType.FIRST,
                "admin-1",
                "shift",
                Instant.now().plusSeconds(86400)
        );
    }

    @Test
    void toRouteAssignmentDataPopulatesVehicleAndRouteInfo() {
        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(vehicle));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.just(route));

        StepVerifier.create(mapper.toRouteAssignmentData(assignment))
                .assertNext(data -> {
                    assertEquals(assignment.getId().getValue(), data.id());
                    assertEquals(vehicle.getLicensePlate(), data.vehicleLicensePlate());
                    assertEquals("dev-1", data.vehicleDeviceId());
                    assertEquals("29A", data.routeNumber());
                    assertEquals("Main", data.routeName());
                    assertEquals("FIRST", data.shiftType());
                    assertEquals("admin-1", data.assignedBy());
                })
                .verifyComplete();
    }

    @Test
    void toRouteAssignmentDataHandlesMissingVehicle() {
        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.empty());
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.just(route));

        StepVerifier.create(mapper.toRouteAssignmentData(assignment))
                .assertNext(data -> {
                    assertNull(data.vehicleLicensePlate());
                    assertNull(data.vehicleDeviceId());
                    assertEquals("29A", data.routeNumber());
                })
                .verifyComplete();
    }

    @Test
    void toRouteAssignmentDataHandlesMissingRoute() {
        when(vehicleRepository.findById(any(VehicleId.class))).thenReturn(Mono.just(vehicle));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.empty());

        StepVerifier.create(mapper.toRouteAssignmentData(assignment))
                .assertNext(data -> {
                    assertNull(data.routeNumber());
                    assertNull(data.routeName());
                    assertEquals(vehicle.getLicensePlate(), data.vehicleLicensePlate());
                })
                .verifyComplete();
    }

    @Test
    void toRouteAssignmentDataReturnsEmptyMonoWhenAssignmentIsNull() {
        StepVerifier.create(mapper.toRouteAssignmentData(null))
                .verifyComplete();
    }
}
