package biz.ugur.busroutebackend.transport.infrastructure.persistence.repository;

import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.BusRouteEntityMapper;
import biz.ugur.busroutebackend.transport.infrastructure.mapper.BusStopEntityMapper;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.r2dbc.core.DatabaseClient;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StopAndRouteRowMapperUpdatedByTest {

    @Mock
    private Row row;
    @Mock
    private RowMetadata metadata;

    @Test
    void stopRowMapperReadsUpdatedByColumn() {
        R2dbcBusStopRepository repository = new R2dbcBusStopRepository(
                mock(DatabaseClient.class), new BusStopEntityMapper(),
                mock(biz.ugur.busroutebackend.routing.infrastructure.config.ETAProperties.class));

        Map<String, String> strings = Map.of(
                "id", "stop-0001",
                "stop_name", "Merkez",
                "stop_code", "AS001",
                "city_id", "city-001",
                "updated_by", "admin-timur");
        lenient().when(row.get(anyString(), eq(String.class)))
                .thenAnswer(inv -> strings.get(inv.getArgument(0, String.class)));
        lenient().when(row.get(anyString(), eq(Boolean.class))).thenReturn(Boolean.TRUE);
        lenient().when(row.get(eq("latitude"), eq(BigDecimal.class))).thenReturn(new BigDecimal("37.96"));
        lenient().when(row.get(eq("longitude"), eq(BigDecimal.class))).thenReturn(new BigDecimal("58.33"));
        lenient().when(row.get(eq("version"), eq(Long.class))).thenReturn(0L);

        BusStop stop = repository.getRowMapper().apply(row, metadata);

        assertThat(stop.getUpdatedBy())
                .as("updated_by из строки БД обязан доезжать до домена — "
                        + "иначе админка не покажет кто редактировал остановку")
                .isEqualTo("admin-timur");
    }

    @Test
    void routeRowMapperReadsUpdatedByColumn() {
        R2dbcBusRouteRepository repository = new R2dbcBusRouteRepository(
                mock(DatabaseClient.class), new BusRouteEntityMapper());

        Map<String, String> strings = Map.of(
                "id", "route-0001",
                "route_number", "34",
                "route_name", "Bagyr",
                "route_color", "#FF5722",
                "city_id", "city-001",
                "updated_by", "admin-timur");
        lenient().when(row.get(anyString(), eq(String.class)))
                .thenAnswer(inv -> strings.get(inv.getArgument(0, String.class)));
        lenient().when(row.get(anyString(), eq(Boolean.class))).thenReturn(Boolean.TRUE);
        lenient().when(row.get(anyString(), eq(Integer.class))).thenReturn(30);
        lenient().when(row.get(eq("version"), eq(Long.class))).thenReturn(0L);

        BusRoute route = repository.getRowMapper().apply(row, metadata);

        assertThat(route.getUpdatedBy())
                .as("updated_by из строки БД обязан доезжать до домена — "
                        + "иначе админка не покажет кто редактировал маршрут")
                .isEqualTo("admin-timur");
    }
}
