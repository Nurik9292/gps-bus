package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
public class RouteStopDetail extends ValueObject {

    private final String stopId;
    private final String stopName;
    private final String stopCode;
    private final Integer sequence;
    private final Integer direction;
    private final Integer estimatedTravelTimeMinutes;
    private final Integer distanceFromStartMeters;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final Boolean isMajorStop;

    public RouteStopDetail(String stopId,
                           String stopName,
                           String stopCode,
                           Integer sequence,
                           Integer direction,
                           Integer estimatedTravelTimeMinutes,
                           Integer distanceFromStartMeters,
                           BigDecimal latitude,
                           BigDecimal longitude,
                           Boolean isMajorStop) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.stopCode = stopCode;
        this.sequence = sequence;
        this.direction = direction;
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        this.distanceFromStartMeters = distanceFromStartMeters;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isMajorStop = isMajorStop;


    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RouteStopDetail that)) return false;
        return Objects.equals(stopId, that.stopId) &&
                Objects.equals(stopName, that.stopName) &&
                Objects.equals(stopCode, that.stopCode) &&
                Objects.equals(sequence, that.sequence) &&
                Objects.equals(direction, that.direction) &&
                Objects.equals(estimatedTravelTimeMinutes, that.estimatedTravelTimeMinutes) &&
                Objects.equals(distanceFromStartMeters, that.distanceFromStartMeters) &&
                Objects.equals(latitude, that.latitude) &&
                Objects.equals(longitude, that.longitude) &&
                Objects.equals(isMajorStop, that.isMajorStop);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId,
                stopName,
                stopCode,
                sequence, direction,
                estimatedTravelTimeMinutes,
                distanceFromStartMeters,
                latitude,
                longitude,
                isMajorStop);
    }

    @Override
    public String toString() {
        return "RouteStopDetail{" +
                "stopId='" + stopId + '\'' +
                ", stopName='" + stopName + '\'' +
                ", stopCode='" + stopCode + '\'' +
                ", sequence=" + sequence +
                ", direction=" + direction +
                ", estimatedTravelTimeMinutes=" + estimatedTravelTimeMinutes +
                ", distanceFromStartMeters=" + distanceFromStartMeters +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", isMajorStop=" + isMajorStop +
                '}';
    }

    public boolean isMajorStop() {
        return isMajorStop != null && isMajorStop;
    }


    public int getTravelTimeMinutes() {
        return estimatedTravelTimeMinutes != null ? estimatedTravelTimeMinutes : 0;
    }


    public int getDistanceMeters() {
        return distanceFromStartMeters != null ? distanceFromStartMeters : 0;
    }


    public double getDistanceKm() {
        return getDistanceMeters() / 1000.0;
    }
}
