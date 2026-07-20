package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.VehicleEntityMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.r2dbc.core.DatabaseClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VehicleRowMapperCityIdTest {

    @Mock
    private Row row;
    @Mock
    private RowMetadata metadata;

    @Test
    void rowMapperReadsCityIdColumn() {
        R2dbcVehicleRepository repository = new R2dbcVehicleRepository(
                mock(DatabaseClient.class), new VehicleEntityMapper(),
                mock(biz.ugur.busroutebackend.shared.infrastructure.persistence.OptimisticLockMetricsRecorder.class),
                mock(biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer.class));

        Map<String, String> strings = Map.of(
                "id", "veh-1",
                "device_id", "999000000000001",
                "license_plate", "6181 AGJ",
                "gps_provider", "CHINA",
                "city_id", "city-001");
        lenient().when(row.get(anyString(), eq(String.class)))
                .thenAnswer(inv -> strings.get(inv.getArgument(0, String.class)));
        lenient().when(row.get(anyString(), eq(Boolean.class))).thenReturn(Boolean.TRUE);
        lenient().when(row.get(eq("route_confidence"), eq(Integer.class))).thenReturn(0);
        lenient().when(row.get(eq("version"), eq(Long.class))).thenReturn(0L);

        Vehicle vehicle = repository.getRowMapper().apply(row, metadata);

        assertThat(vehicle.getCityId())
                .as("city_id из строки БД обязан доезжать до домена — "
                        + "иначе city-aware импорт видит все борта безгородными")
                .isNotNull();
        assertThat(vehicle.getCityId().getValue()).isEqualTo("city-001");
    }
}
