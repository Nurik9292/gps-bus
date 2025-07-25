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
    private String stopCode; // Код для пассажиров "ASH001"

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("stop_sequence")
    private Integer stopSequence; // Порядок в маршруте (1, 2, 3...)

    @JsonProperty("estimated_travel_time_minutes")
    private Integer estimatedTravelTimeMinutes; // Время до следующей остановки

    @JsonProperty("distance_from_start_meters")
    private Integer distanceFromStartMeters; // Расстояние от начала маршрута

    @JsonProperty("is_major_stop")
    private Boolean isMajorStop; // Крупная остановка (транспортный узел)

    @JsonProperty("has_shelter")
    private Boolean hasShelter; // Есть ли навес

    @JsonProperty("is_accessible")
    private Boolean isAccessible; // Доступность для инвалидов

    // Дополнительная информация для клиента
    @JsonProperty("cumulative_travel_time_minutes")
    private Integer cumulativeTravelTimeMinutes; // Время от начала маршрута

    public RouteStopDTO(String stopId, String stopName, String stopCode,
                        Double latitude, Double longitude, Integer stopSequence,
                        Integer estimatedTravelTimeMinutes, Integer distanceFromStartMeters,
                        Boolean isMajorStop, Boolean hasShelter) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.stopCode = stopCode;
        this.latitude = latitude;
        this.longitude = longitude;
        this.stopSequence = stopSequence;
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        this.distanceFromStartMeters = distanceFromStartMeters;
        this.isMajorStop = isMajorStop;
        this.hasShelter = hasShelter;
        this.isAccessible = false; // По умолчанию
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
