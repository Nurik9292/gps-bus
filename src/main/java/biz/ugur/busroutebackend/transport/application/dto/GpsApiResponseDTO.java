package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class GpsApiResponseDTO {

    @JsonProperty("code")
    private Integer code;

    @JsonProperty("msg")
    private String msg;

    @JsonProperty("traceId")
    private String traceId;

    @JsonProperty("data")
    private List<GpsPositionDTO> data;

    public boolean isSuccess() {
        return code != null && code == 1;
    }
}
