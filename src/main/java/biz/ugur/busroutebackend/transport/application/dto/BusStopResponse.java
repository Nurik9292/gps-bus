package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

@Data
public class BusStopResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("stop_name")
    private String stopName;

    @JsonProperty("stop_code")
    private String stopCode;

    @JsonProperty("latitude")
    private BigDecimal latitude;

    @JsonProperty("longitude")
    private BigDecimal longitude;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("is_major_stop")
    private Boolean isMajorStop;

    @JsonProperty("has_shelter")
    private Boolean hasShelter;

    @JsonProperty("serving_routes_count")
    private Integer servingRoutesCount;

    @JsonProperty("created_at")
    private Instant createdAt;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    public BusStopResponse(String id, String stopName, String stopCode, BigDecimal latitude,
                           BigDecimal longitude, Boolean isActive, Boolean isMajorStop,
                           Boolean hasShelter, Integer servingRoutesCount) {
        this.id = id;
        this.stopName = stopName;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = isActive;
        this.isMajorStop = isMajorStop;
        this.hasShelter = hasShelter;
        this.servingRoutesCount = servingRoutesCount;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}