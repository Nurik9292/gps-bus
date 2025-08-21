package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.Getter;

import java.util.Objects;

@Getter
public class StopRouteDetail extends ValueObject {

    private final String routeId;
    private final String routeName;
    private final String routeNumber;
    private final Integer direction;
    private final Integer estimatedTravelTimeMinutes;
    private final Integer distanceFromStartMeters;

    public StopRouteDetail(String routeId,
                           String routeName,
                           String routeNumber,
                           Integer direction,
                           Integer estimatedTravelTimeMinutes,
                           Integer distanceFromStartMeters) {
        this.routeId = routeId;
        this.routeName = routeName;
        this.routeNumber = routeNumber;
        this.direction = direction;
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        this.distanceFromStartMeters = distanceFromStartMeters;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof StopRouteDetail that)) return false;
        return Objects.equals(routeId, that.routeId) &&
                Objects.equals(routeName, that.routeName) &&
                Objects.equals(routeNumber, that.routeNumber) &&
                Objects.equals(direction, that.direction) &&
                Objects.equals(estimatedTravelTimeMinutes, that.estimatedTravelTimeMinutes) &&
                Objects.equals(distanceFromStartMeters, that.distanceFromStartMeters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(routeId, routeName, routeNumber, direction, estimatedTravelTimeMinutes, distanceFromStartMeters);
    }

    @Override
    public String toString() {
        return "StopRouteDetail{" +
                "routeId='" + routeId + '\'' +
                ", routeName='" + routeName + '\'' +
                ", routeNumber='" + routeNumber + '\'' +
                ", direction=" + direction +
                ", estimatedTravelTimeMinutes=" + estimatedTravelTimeMinutes +
                ", distanceFromStartMeters=" + distanceFromStartMeters +
                '}';
    }
}
