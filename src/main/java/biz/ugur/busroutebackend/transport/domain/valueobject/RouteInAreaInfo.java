package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

@ToString
@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteInAreaInfo extends ValueObject {

    private final String routeId;
    private final String routeNumber;
    private final String routeName;
    private final String routeColor;
    private final Integer direction;
    private final Double nearestPointLatitude;
    private final Double nearestPointLongitude;
    private final Double distanceToCenterMeters;

    public RouteInAreaInfo(String routeId, String routeNumber, String routeName, String routeColor,
                           Integer direction, Double nearestPointLatitude, Double nearestPointLongitude,
                           Double distanceToCenterMeters) {
        this.routeId = Objects.requireNonNull(routeId, "Route ID cannot be null");
        this.routeNumber = Objects.requireNonNull(routeNumber, "Route number cannot be null");
        this.routeName = routeName;
        this.routeColor = routeColor;
        this.direction = direction;
        this.nearestPointLatitude = nearestPointLatitude;
        this.nearestPointLongitude = nearestPointLongitude;
        this.distanceToCenterMeters = distanceToCenterMeters;
    }

    public boolean isForwardDirection() {
        return direction != null && direction == 0;
    }

    public boolean isBackwardDirection() {
        return direction != null && direction == 1;
    }


}