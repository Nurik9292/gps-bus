package biz.ugur.busroutebackend.interfaces.websocket;

import biz.ugur.busroutebackend.transport.application.services.VehicleCityIndex;
import biz.ugur.busroutebackend.transport.application.usecase.GetActiveVehiclesUseCase;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WsOutboundStreamBuilderCityFilterTest {

    @Mock
    private GetActiveVehiclesUseCase getActiveVehiclesUseCase;
    @Mock
    private WsBroadcastSink broadcastSink;
    @Mock
    private PipelineTracer pipelineTracer;
    @Mock
    private VehicleCityIndex vehicleCityIndex;

    private WsOutboundStreamBuilder builder;
    private SessionConfig config;

    @BeforeEach
    void setUp() {
        builder = new WsOutboundStreamBuilder(getActiveVehiclesUseCase, new ObjectMapper(),
                broadcastSink, pipelineTracer, vehicleCityIndex);
        config = new SessionConfig();
        config.setRouteFilter(Set.of("1"));
        config.setSubscriptionType("routes");
    }

    @Test
    void withoutCityFilterEveryVehiclePasses() {
        assertThat(builder.sessionCityAllows("veh-any", config)).isTrue();
    }

    @Test
    void cityFilterDropsVehicleFromAnotherCity() {
        config.setCityFilter("city-006");
        when(vehicleCityIndex.cityOf("veh-ashgabat")).thenReturn(Optional.of("city-001"));

        assertThat(builder.sessionCityAllows("veh-ashgabat", config)).isFalse();
    }

    @Test
    void cityFilterKeepsVehicleOfSameCity() {
        config.setCityFilter("city-006");
        when(vehicleCityIndex.cityOf("veh-arkadag")).thenReturn(Optional.of("city-006"));

        assertThat(builder.sessionCityAllows("veh-arkadag", config)).isTrue();
    }

    @Test
    void vehicleWithUnknownCityIsNotDropped() {
        config.setCityFilter("city-006");
        when(vehicleCityIndex.cityOf("veh-unknown")).thenReturn(Optional.empty());

        assertThat(builder.sessionCityAllows("veh-unknown", config)).isTrue();
    }
}
