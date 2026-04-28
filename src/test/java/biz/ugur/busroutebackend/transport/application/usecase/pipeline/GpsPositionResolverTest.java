package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import biz.ugur.busroutebackend.transport.domain.valueobject.VehicleId;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class GpsPositionResolverTest {

    private PipelineTracer pipelineTracer;
    private GpsPositionResolver resolver;

    @BeforeEach
    void setUp() {
        pipelineTracer = mock(PipelineTracer.class);
        resolver = new GpsPositionResolver(pipelineTracer);
    }

    private GpsPositionDTO position(String deviceId, LocalDateTime fixTime, GpsProviderType provider) {
        GpsPositionDTO p = new GpsPositionDTO();
        p.setDeviceId(deviceId);
        p.setFixTime(fixTime);
        p.setLatitude(37.95);
        p.setLongitude(58.35);
        p.setGpsProvider(provider);
        return p;
    }

    private Vehicle vehicle(String deviceId, String plate, GpsProviderType provider) {
        return Vehicle.restore(
                VehicleId.generate(),
                deviceId,
                plate,
                null, null, null, null,
                null, null, null, true,
                0.0, null, null, null, null, null, false,
                null, 0, true,
                provider,
                LocalDateTime.now(), LocalDateTime.now(), 0L
        );
    }

    @Test
    void emptyInputProducesEmptyMap() {
        Map<String, GpsPositionDTO> result = resolver.resolveLatestByDevice(List.of(), Map.of());
        assertThat(result).isEmpty();
    }

    @Test
    void singlePositionPerDeviceIsReturnedAsIs() {
        LocalDateTime now = LocalDateTime.now();
        List<GpsPositionDTO> positions = List.of(
                position("dev-1", now, GpsProviderType.CHINA),
                position("dev-2", now, GpsProviderType.CHINA)
        );

        Map<String, GpsPositionDTO> result = resolver.resolveLatestByDevice(positions, Map.of());

        assertThat(result).hasSize(2);
        assertThat(result).containsKeys("dev-1", "dev-2");
    }

    @Test
    void laterFixTimeWinsForSameDevice() {
        LocalDateTime t1 = LocalDateTime.now().minusSeconds(30);
        LocalDateTime t2 = LocalDateTime.now();

        List<GpsPositionDTO> positions = List.of(
                position("dev-1", t1, GpsProviderType.CHINA),
                position("dev-1", t2, GpsProviderType.TUGDK)
        );

        Map<String, GpsPositionDTO> result = resolver.resolveLatestByDevice(positions, Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get("dev-1").getFixTime()).isEqualTo(t2);
        assertThat(result.get("dev-1").getGpsProvider()).isEqualTo(GpsProviderType.TUGDK);
    }

    @Test
    void firstPositionWinsWhenLaterHasNullFixTime() {
        LocalDateTime t1 = LocalDateTime.now().minusSeconds(30);

        List<GpsPositionDTO> positions = List.of(
                position("dev-1", t1, GpsProviderType.CHINA),
                position("dev-1", null, GpsProviderType.TUGDK)
        );

        Map<String, GpsPositionDTO> result = resolver.resolveLatestByDevice(positions, Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get("dev-1").getFixTime()).isEqualTo(t1);
    }

    @Test
    void laterPositionWinsWhenExistingHasNullFixTime() {
        LocalDateTime t1 = LocalDateTime.now();

        List<GpsPositionDTO> positions = List.of(
                position("dev-1", null, GpsProviderType.CHINA),
                position("dev-1", t1, GpsProviderType.TUGDK)
        );

        Map<String, GpsPositionDTO> result = resolver.resolveLatestByDevice(positions, Map.of());

        assertThat(result).hasSize(1);
        assertThat(result.get("dev-1").getFixTime()).isEqualTo(t1);
        assertThat(result.get("dev-1").getGpsProvider()).isEqualTo(GpsProviderType.TUGDK);
    }

    @Test
    void duplicatesArePreservedInOrderForExistingVehicles() {
        LocalDateTime t1 = LocalDateTime.now().minusSeconds(30);
        LocalDateTime t2 = LocalDateTime.now();

        List<GpsPositionDTO> positions = List.of(
                position("dev-1", t1, GpsProviderType.CHINA),
                position("dev-1", t2, GpsProviderType.TUGDK),
                position("dev-2", t1, GpsProviderType.CHINA)
        );

        Vehicle existing = vehicle("dev-1", "AG-100", GpsProviderType.CHINA);
        Map<String, Vehicle> existingVehicles = Map.of("dev-1", existing);

        Map<String, GpsPositionDTO> result = resolver.resolveLatestByDevice(positions, existingVehicles);

        assertThat(result).hasSize(2);
        assertThat(result.get("dev-1").getGpsProvider()).isEqualTo(GpsProviderType.TUGDK);
    }

    @Test
    void multipleDevicesEachKeepLatestPosition() {
        LocalDateTime t1 = LocalDateTime.now().minusSeconds(30);
        LocalDateTime t2 = LocalDateTime.now();

        List<GpsPositionDTO> positions = List.of(
                position("dev-1", t1, GpsProviderType.CHINA),
                position("dev-2", t1, GpsProviderType.CHINA),
                position("dev-1", t2, GpsProviderType.CHINA),
                position("dev-2", t2, GpsProviderType.TUGDK)
        );

        Map<String, GpsPositionDTO> result = resolver.resolveLatestByDevice(positions, Map.of());

        assertThat(result).hasSize(2);
        assertThat(result.get("dev-1").getFixTime()).isEqualTo(t2);
        assertThat(result.get("dev-2").getFixTime()).isEqualTo(t2);
    }
}
