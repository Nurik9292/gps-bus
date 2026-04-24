package biz.ugur.busroutebackend.transport.application.dto;


import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Duration;
import java.time.LocalDateTime;


@Data
public class GpsPositionDTO {

    @JsonProperty("deviceId")
    private String deviceId;

    private GpsProviderType gpsProvider;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("speed")
    private Double speed;

    @JsonProperty("fixTime")
    private LocalDateTime fixTime;

    @JsonProperty("course")
    private Double course;

    @JsonProperty("attributes")
    private GpsAttributesDTO attributes;

    @JsonProperty("reportTime")
    private String reportTime;

    @JsonProperty("utcTime")
    private String utcTime;

    @JsonProperty("onlineState")
    private String onlineState;

    @JsonProperty("serverTime")
    private LocalDateTime serverTime;

    @JsonProperty("deviceTime")
    private LocalDateTime deviceTime;

    @JsonProperty("hdop")
    private Double hdop;

    @JsonProperty("satellites")
    private Integer satellites;

    @JsonProperty("accuracy")
    private Double accuracy;

    public boolean isOnline() {
        return "Online".equalsIgnoreCase(onlineState);
    }

    public boolean isLikelyBuffered() {
        if (serverTime == null || deviceTime == null) {
            return false;
        }
        return Duration.between(deviceTime, serverTime).toSeconds() > 30;
    }

    public boolean isHighQualityFix() {
        return (hdop == null || hdop <= 5.0) && (satellites == null || satellites >= 4);
    }

    public String getVehicleName() {
        return attributes != null ? attributes.getName() : null;
    }


    public Boolean getMotion() {
        if (attributes != null && attributes.getMotion() != null) {
            return attributes.getMotion();
        }
        return speed != null && speed > 0;
    }

    @Data
    public static class GpsAttributesDTO {

        @JsonProperty("name")
        private String name;

        @JsonProperty("uniqueId")
        private String uniqueId;

        @JsonProperty("motion")
        private Boolean motion;

        @JsonProperty("ignition")
        private Boolean ignition;
    }
}
