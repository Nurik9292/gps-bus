package biz.ugur.busroutebackend.interfaces.rest.routing.V2.response;

import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TripOptionV2DTOTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesCamelCaseWithInitialWaiting() throws Exception {
        TripOptionDTO v1 = new TripOptionDTO("opt-1", "direct", "Прямой 35 мин",
                35, 4, 0, List.of());
        TripOptionV2DTO v2 = TripOptionV2DTO.fromV1(v1, 15);

        String json = objectMapper.writeValueAsString(v2);

        assertThat(json).contains("\"totalTravelMinutes\":35");
        assertThat(json).contains("\"totalWalkingMinutes\":4");
        assertThat(json).contains("\"initialWaitingMinutes\":15");
        assertThat(json).contains("\"tripType\":\"direct\"");
        assertThat(json).doesNotContain("_");
    }

    @Test
    void exposesInitialWaitingValue() {
        TripOptionDTO v1 = new TripOptionDTO("opt-1", "direct", "summary", 35, 4, 0, List.of());

        TripOptionV2DTO v2 = TripOptionV2DTO.fromV1(v1, 15);

        assertThat(v2.initialWaitingMinutes()).isEqualTo(15);
        assertThat(v2.totalTravelMinutes()).isEqualTo(35);
    }
}
