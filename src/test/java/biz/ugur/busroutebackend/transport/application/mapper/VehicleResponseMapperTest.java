package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;


class VehicleResponseMapperTest {

    private final VehicleResponseMapper mapper = new VehicleResponseMapper();
    private Vehicle vehicle;

    @BeforeEach
    void setUp() {
        vehicle = Vehicle.create("dev-1", "1234 AGH")
                .updatePosition(37.9601, 58.3261, 45.0, LocalDateTime.now(), 180.0);
    }

    @Test
    void toPositionDTOPopulatesCoreFieldsWhenRouteIsNull() {
        StepVerifier.create(mapper.toPositionDTO(vehicle))
                .assertNext(dto -> {
                    assertEquals(vehicle.getId().getValue(), dto.getVehicleId());
                    assertEquals("dev-1", dto.getDeviceId());
                    assertEquals(37.9601, dto.getCurrentLatitude());
                    assertEquals(58.3261, dto.getCurrentLongitude());
                    assertEquals(45.0, dto.getSpeedKmh());
                    assertNotNull(dto.getLastPositionUpdate());
                    assertNull(dto.getRouteNumber());
                    assertNull(dto.getRouteName());
                })
                .verifyComplete();
    }

    @Test
    void toPositionDTOWithRouteCopiesRouteNumberAndName() {
        BusRoute route = BusRoute.create("29A", "Main line", "", "", "#FF5722", "ashgabat", 30);

        StepVerifier.create(mapper.toPositionDTO(vehicle, route))
                .assertNext(dto -> {
                    assertEquals("29A", dto.getRouteNumber());
                    assertEquals("Main line", dto.getRouteName());
                })
                .verifyComplete();
    }

    @Test
    void toPositionDTOSyncReturnsDtoDirectly() {
        VehiclePositionDTO dto = mapper.toPositionDTOSync(vehicle, "15", "Ring");

        assertEquals(vehicle.getId().getValue(), dto.getVehicleId());
        assertEquals("15", dto.getRouteNumber());
        assertEquals("Ring", dto.getRouteName());
        assertEquals(45.0, dto.getSpeedKmh());
        assertNotNull(dto.getCreatedAt());
    }

    @Test
    void toPositionDTOCopiesDeviceIdAndHasTimestamp() {
        Vehicle fresh = Vehicle.create("dev-9", "9876 AGH");

        StepVerifier.create(mapper.toPositionDTO(fresh))
                .assertNext(dto -> {
                    assertNotNull(dto.getLastPositionUpdate());
                    assertEquals("dev-9", dto.getDeviceId());
                })
                .verifyComplete();
    }
}
