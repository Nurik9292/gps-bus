package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import lombok.Getter;

import java.util.Objects;

@Getter
public class RouteStopInfo extends ValueObject {

    String stopId;
    String stopName;
    Integer sequence;
    Integer direction;
    Integer estimatedTravelTime;
    Integer distanceFromStart;
    Double latitude;
    Double longitude;

    public RouteStopInfo() {}

    public RouteStopInfo(String stopId,
                         String stopName,
                         Integer sequence,
                         Integer direction,
                         Integer estimatedTravelTime,
                         Integer distanceFromStart,
                         Double latitude,
                         Double longitude) {
        this.stopId = stopId;
        this.stopName = stopName;
        this.sequence = sequence;
        this.direction = direction;
        this.estimatedTravelTime = estimatedTravelTime;
        this.distanceFromStart = distanceFromStart;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RouteStopInfo that)) return false;
        return Objects.equals(stopId, that.stopId) &&
                Objects.equals(stopName, that.stopName) &&
                Objects.equals(sequence, that.sequence) &&
                Objects.equals(direction, that.direction) &&
                Objects.equals(estimatedTravelTime, that.estimatedTravelTime) &&
                Objects.equals(distanceFromStart, that.distanceFromStart) &&
                Objects.equals(latitude, that.latitude) &&
                Objects.equals(longitude, that.longitude);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stopId, stopName, sequence, direction, estimatedTravelTime, distanceFromStart, latitude, longitude);
    }

    @Override
    public String toString() {
        return "RouteStopInfo{" +
                "stopId='" + stopId + '\'' +
                ", stopName='" + stopName + '\'' +
                ", sequence=" + sequence +
                ", direction=" + direction +
                ", estimatedTravelTime=" + estimatedTravelTime +
                ", distanceFromStart=" + distanceFromStart +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                '}';
    }
}

