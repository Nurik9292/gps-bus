package biz.ugur.busroutebackend.interfaces.rest.routing.V2.response;

import biz.ugur.busroutebackend.routing.application.dto.TripOptionDTO;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import lombok.Getter;

@Getter
public class TripOptionV2DTO {

    @JsonUnwrapped
    private final TripOptionDTO option;

    @JsonProperty("initial_waiting_minutes")
    private final int initialWaitingMinutes;

    public TripOptionV2DTO(TripOptionDTO option, int initialWaitingMinutes) {
        this.option = option;
        this.initialWaitingMinutes = initialWaitingMinutes;
    }
}
