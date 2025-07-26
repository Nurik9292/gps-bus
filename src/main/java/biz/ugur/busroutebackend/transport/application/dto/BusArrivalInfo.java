package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusArrivalInfo {
    private String vehicleId;
    private String licensePlate;           // "1234 AGH"
    private String routeNumber;           // "29"
    private String routeName;             // "Маршрут 29"
    private String routeColor;            // "#1976D2"
    private Integer estimatedArrivalMinutes; // 5 минут
    private String arrivalStatus;         // "approaching", "at_stop", "passed"
    private Double currentLatitude;
    private Double currentLongitude;
    private Double speedKmh;
    private Boolean isInMotion;
    private String currentStopName;       // "Текущая остановка"
    private LocalDateTime lastUpdated;

    // Конструкторы, геттеры, сеттеры
    public BusArrivalInfo() {}

    public BusArrivalInfo(String vehicleId, String licensePlate, String routeNumber, String routeName,
                          String routeColor, Integer estimatedArrivalMinutes, String arrivalStatus,
                          Double currentLatitude, Double currentLongitude, Double speedKmh,
                          Boolean isInMotion, String currentStopName, LocalDateTime lastUpdated) {
        this.vehicleId = vehicleId;
        this.licensePlate = licensePlate;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.estimatedArrivalMinutes = estimatedArrivalMinutes;
        this.arrivalStatus = arrivalStatus;
        this.currentLatitude = currentLatitude;
        this.currentLongitude = currentLongitude;
        this.speedKmh = speedKmh;
        this.isInMotion = isInMotion;
        this.currentStopName = currentStopName;
        this.lastUpdated = lastUpdated;
    }

    // Вспомогательные методы
    public String getDisplayText() {
        if (estimatedArrivalMinutes <= 1) {
            return "Прибывает";
        } else if (estimatedArrivalMinutes >= 60) {
            return "Более часа";
        } else {
            return estimatedArrivalMinutes + " мин";
        }
    }

    public boolean isComingSoon() {
        return estimatedArrivalMinutes <= 5;
    }


}


