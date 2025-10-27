package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.stop;

import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class BusStopResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("city_id")
    private String cityId;

    @JsonProperty("stop_name")
    private String stopName;

    @JsonProperty("name_en")
    private String nameEn;

    @JsonProperty("name_tm")
    private String nameTm;

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

    @JsonProperty("serving_routes_count")
    private Integer servingRoutesCount;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    public BusStopResponse(String id,
                           String stopName,
                           String nameEn,
                           String nameTm,
                           String stopCode,
                           BigDecimal latitude,
                           BigDecimal longitude,
                           Boolean isActive,
                           Boolean isMajorStop,
                           Integer servingRoutesCount,
                           LocalDateTime createdAt,
                           LocalDateTime updatedAt,
                           String cityId
    ) {
        this.id = id;
        this.stopName = stopName;
        this.nameEn = nameEn;
        this.nameTm = nameTm;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isActive = isActive;
        this.isMajorStop = isMajorStop;
        this.servingRoutesCount = servingRoutesCount;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.cityId = cityId;
    }

    public String getDisplayName(String language) {
        return switch (language != null ? language.toLowerCase() : "ru") {
            case "en" -> nameEn != null ? nameEn : stopName;
            case "tm" -> nameTm != null ? nameTm : stopName;
            default -> stopName;
        };
    }

    public static BusStopResponse fromResult(StopData result) {
        return new BusStopResponse(
                result.id(),
                result.stopName(),
                result.nameEn(),
                result.nameTm(),
                result.stopCode(),
                result.latitude(),
                result.longitude(),
                result.isActive(),
                result.isMajorStop(),
                result.servingRouteCount(),
                result.createdAt(),
                result.updatedAt(),
                result.cityId()
        );
    }
}