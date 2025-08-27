package biz.ugur.busroutebackend.transport.application.dto;


import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class GpsPositionDTO {

    @JsonProperty("deviceId")
    private String deviceId;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("speed")
    private Double speed;

    @JsonProperty("fixTime")
    private Instant fixTime;

    @JsonProperty("course")
    private Double course;

    @JsonProperty("attributes")
    private GpsAttributesDTO attributes;

    public String getVehicleName() {
        return attributes != null ? attributes.getName() : null;
    }

    public Boolean getMotion() {
        return attributes != null ? attributes.getMotion() : null;
    }

    @Data
    public static class GpsAttributesDTO {

        @JsonProperty("name")
        private String name;

        @JsonProperty("motion")
        private Boolean motion;

        @JsonProperty("ignition")
        private Boolean ignition;
    }
}
