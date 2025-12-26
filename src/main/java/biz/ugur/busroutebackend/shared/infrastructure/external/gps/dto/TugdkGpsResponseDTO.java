package biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TugdkGpsResponseDTO {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("deviceId")
    private Integer deviceId;

    @JsonProperty("protocol")
    private String protocol;

    @JsonProperty("serverTime")
    private String serverTime;

    @JsonProperty("deviceTime")
    private String deviceTime;

    @JsonProperty("fixTime")
    private String fixTime;

    @JsonProperty("valid")
    private Boolean valid;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("altitude")
    private Double altitude;

    @JsonProperty("speed")
    private Double speed;

    @JsonProperty("course")
    private Double course;

    @JsonProperty("accuracy")
    private Double accuracy;

    @JsonProperty("network")
    private Object network;

    @JsonProperty("address")
    private String address;

    @JsonProperty("geofenceIds")
    private int[] geofenceIds;

    @JsonProperty("attributes")
    private TugdkAttributesDTO attributes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TugdkAttributesDTO {

        @JsonProperty("name")
        private String name;

        @JsonProperty("uniqueId")
        private String uniqueId;

        @JsonProperty("motion")
        private Boolean motion;

        @JsonProperty("sat")
        private Integer sat;

        @JsonProperty("hdop")
        private Double hdop;

        @JsonProperty("event")
        private Integer event;

        @JsonProperty("deviceTemp")
        private Integer deviceTemp;

        @JsonProperty("power")
        private Double power;

        @JsonProperty("battery")
        private Double battery;

        @JsonProperty("adc1")
        private Integer adc1;

        @JsonProperty("adc2")
        private Integer adc2;

        @JsonProperty("odometer")
        private Long odometer;

        @JsonProperty("operator")
        private Integer operator;

        @JsonProperty("distance")
        private Double distance;

        @JsonProperty("totalDistance")
        private Double totalDistance;

        @JsonProperty("stopTime")
        private String stopTime;

        @JsonProperty("in1")
        private Integer in1;

        @JsonProperty("in2")
        private Integer in2;

        @JsonProperty("in4")
        private Integer in4;

        @JsonProperty("io27")
        private Integer io27;

        @JsonProperty("io28")
        private Integer io28;

        public boolean isIgnitionOn() {
            return in4 != null && in4 == 1;
        }
    }

    public Double getSpeedKmh() {
        if (speed == null) {
            return 0.0;
        }
        return speed * 1.852;
    }

    public boolean isValidPosition() {
        return Boolean.TRUE.equals(valid)
                && latitude != null
                && longitude != null
                && attributes != null
                && attributes.getUniqueId() != null;
    }
}
