package biz.ugur.busroutebackend.interfaces.rest.routing.V2.response;

import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripOptionV2DTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void exposesInitialWaitingAlongsideFlattenedBaseFields() throws Exception {
        TripOptionDTO base = new TripOptionDTO("opt-1", "direct", "Прямой 35 мин",
                35, 0, 0, List.of());
        TripOptionV2DTO v2 = new TripOptionV2DTO(base, 15);

        String json = objectMapper.writeValueAsString(v2);

        assertThat(json).contains("\"total_travel_minutes\":35");
        assertThat(json).contains("\"initial_waiting_minutes\":15");
        assertThat(json).doesNotContain("\"base\"");
    }
}
