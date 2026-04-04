package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDateTime;


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BusArrivalInfo {
    private String vehicleId;
    private String licensePlate;
    private String routeId;
    private String routeNumber;
    private String routeName;
    private String routeColor;
    private Integer estimatedArrivalMinutes;
    private String arrivalStatus;
    private Double currentLatitude;
    private Double currentLongitude;
    private Double speedKmh;
    private Boolean isInMotion;
    private String currentStopName;
    private LocalDateTime lastUpdated;
    private Double course;
    private Instant calculatedAt;

    public BusArrivalInfo() {
        this.calculatedAt = Instant.now();
    }

    public BusArrivalInfo(String vehicleId,
                          String licensePlate,
                          String routeId,
                          String routeNumber,
                          String routeName,
                          String routeColor,
                          Integer estimatedArrivalMinutes,
                          String arrivalStatus,
                          Double currentLatitude,
                          Double currentLongitude,
                          Double speedKmh,
                          Boolean isInMotion,
                          String currentStopName,
                          LocalDateTime lastUpdated,
                          Double course) {
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
        this.routeId = routeId;
        this.course = course;
        this.calculatedAt = Instant.now();
    }
}


