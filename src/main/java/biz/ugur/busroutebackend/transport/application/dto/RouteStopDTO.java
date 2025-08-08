package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class RouteStopDTO {

    @JsonProperty("stop_id")
    private String stopId;

    @JsonProperty("stop_name")
    private String stopName;

    @JsonProperty("stop_code")
    private String stopCode;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("stop_sequence")
    private Integer stopSequence;

    @JsonProperty("estimated_travel_time_minutes")
    private Integer estimatedTravelTimeMinutes;

    @JsonProperty("distance_from_start_meters")
    private Integer distanceFromStartMeters;

    @JsonProperty("is_major_stop")
    private Boolean isMajorStop;

    @JsonProperty("is_accessible")
    private Boolean isAccessible;

    @JsonProperty("cumulative_travel_time_minutes")
    private Integer cumulativeTravelTimeMinutes;

    public RouteStopDTO(String stopId,
                        String stopName,
                        String stopCode,
                        Double latitude,
                        Double longitude,
                        Integer stopSequence,
                        Integer estimatedTravelTimeMinutes,
                        Integer distanceFromStartMeters,
                        Boolean isMajorStop) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stopSequence = stopSequence;
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        this.distanceFromStartMeters = distanceFromStartMeters;
        this.isMajorStop = isMajorStop;
        this.isAccessible = false;
    }

    public String getDistanceFromStartText() {
        if (distanceFromStartMeters == null || distanceFromStartMeters == 0) {
            return "Начало маршрута";
        } else if (distanceFromStartMeters < 1000) {
            return distanceFromStartMeters + "м от начала";
        } else {
            return String.format("%.1fкм от начала", distanceFromStartMeters / 1000.0);
        }
    }

    public String getTravelTimeText() {
        if (estimatedTravelTimeMinutes == null || estimatedTravelTimeMinutes == 0) {
            return "Конечная остановка";
        } else {
            return estimatedTravelTimeMinutes + " мин до следующей";
        }
    }
}
