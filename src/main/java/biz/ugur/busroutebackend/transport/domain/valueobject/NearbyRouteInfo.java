package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

@ToString
@Getter
@EqualsAndHashCode(callSuper = false)
public class NearbyRouteInfo extends ValueObject {

    private final String routeId;
    private final String routeNumber;
    private final String routeColor;

    public NearbyRouteInfo(String routeId, String routeNumber, String routeColor) {
        this.routeId = Objects.requireNonNull(routeId, "Route ID cannot be null");
        this.routeNumber = Objects.requireNonNull(routeNumber, "Route number cannot be null");
        this.routeColor = routeColor;
    }
}
