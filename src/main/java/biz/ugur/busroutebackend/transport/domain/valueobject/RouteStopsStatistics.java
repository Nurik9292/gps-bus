package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.valueObjects.ValueObject;
import lombok.Getter;

import java.util.Objects;

@Getter
public class RouteStopsStatistics extends ValueObject {

    private final Long forwardStopsCount;
    private final Long backwardStopsCount;
    private final Integer forwardTotalTravelTime;
    private final Integer backwardTotalTravelTime;
    private final Integer forwardTotalDistance;
    private final Integer backwardTotalDistance;

    public RouteStopsStatistics(Long forwardStopsCount,
                                Long backwardStopsCount,
                                Integer forwardTotalTravelTime,
                                Integer backwardTotalTravelTime,
                                Integer forwardTotalDistance,
                                Integer backwardTotalDistance) {
        this.forwardStopsCount = forwardStopsCount;
        this.backwardStopsCount = backwardStopsCount;
        this.forwardTotalTravelTime = forwardTotalTravelTime;
        this.backwardTotalTravelTime = backwardTotalTravelTime;
        this.forwardTotalDistance = forwardTotalDistance;
        this.backwardTotalDistance = backwardTotalDistance;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RouteStopsStatistics that)) return false;
        return Objects.equals(forwardStopsCount, that.forwardStopsCount) &&
                Objects.equals(backwardStopsCount, that.backwardStopsCount) &&
                Objects.equals(forwardTotalTravelTime, that.forwardTotalTravelTime) &&
                Objects.equals(backwardTotalTravelTime, that.backwardTotalTravelTime) &&
                Objects.equals(forwardTotalDistance, that.forwardTotalDistance) &&
                Objects.equals(backwardTotalDistance, that.backwardTotalDistance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                forwardStopsCount,
                backwardStopsCount,
                forwardTotalTravelTime,
                backwardTotalTravelTime,
                forwardTotalDistance,
                backwardTotalDistance);
    }

    @Override
    public String toString() {
        return "RouteStopsStatistics{" +
                "forwardStopsCount=" + forwardStopsCount +
                ", backwardStopsCount=" + backwardStopsCount +
                ", forwardTotalTravelTime=" + forwardTotalTravelTime +
                ", backwardTotalTravelTime=" + backwardTotalTravelTime +
                ", forwardTotalDistance=" + forwardTotalDistance +
                ", backwardTotalDistance=" + backwardTotalDistance +
                '}';
    }
}
