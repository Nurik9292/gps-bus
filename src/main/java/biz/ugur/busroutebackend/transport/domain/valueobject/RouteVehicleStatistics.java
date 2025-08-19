package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;


@ToString
@Getter
@EqualsAndHashCode(callSuper = false)
public class RouteVehicleStatistics extends ValueObject {

    private final Long activeVehiclesCount;
    private final Long vehiclesInMotionCount;
    private final Long vehiclesWithRecentPositionCount;

    public RouteVehicleStatistics(Long activeVehiclesCount,
                                  Long vehiclesInMotionCount,
                                  Long vehiclesWithRecentPositionCount) {
        this.activeVehiclesCount = activeVehiclesCount != null ? activeVehiclesCount : 0L;
        this.vehiclesInMotionCount = vehiclesInMotionCount != null ? vehiclesInMotionCount : 0L;
        this.vehiclesWithRecentPositionCount = vehiclesWithRecentPositionCount != null ? vehiclesWithRecentPositionCount : 0L;
    }

    public boolean hasActiveVehicles() {
        return activeVehiclesCount > 0;
    }

    public boolean hasVehiclesInMotion() {
        return vehiclesInMotionCount > 0;
    }

    public boolean hasRecentData() {
        return vehiclesWithRecentPositionCount > 0;
    }



}