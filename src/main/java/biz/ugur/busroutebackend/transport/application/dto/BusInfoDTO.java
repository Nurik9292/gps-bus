package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BusInfoDTO {

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("car_number")
    private String carNumber;

    @JsonProperty("number")
    private String routeNumber;

    @JsonProperty("route_name")
    private String routeName;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("last_update")
    private String lastUpdate;

    @JsonProperty("date")
    private LocalDate assignmentDate;

    @JsonProperty("change")
    private Integer shift;
}