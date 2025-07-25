package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDate;

@Data
public class BusInfoDTO {

    @JsonProperty("car_number")
    private String carNumber;

    @JsonProperty("number")
    private String routeNumber;

    @JsonProperty("date")
    private LocalDate assignmentDate;

    @JsonProperty("change")
    private Integer shift;
}