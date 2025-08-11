package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

@Getter
public class RouteStopsData extends ValueObject {

    List<RouteStopDetail> forwardStops;
    List<RouteStopDetail> backwardStops;

    public RouteStopsData() {
        this.forwardStops = List.of();
        this.backwardStops = List.of();
    }

    public RouteStopsData(List<RouteStopDetail> forwardStops, List<RouteStopDetail> backwardStops) {
        this.forwardStops = forwardStops;
        this.backwardStops = backwardStops;
        if (forwardStops == null) {
            this.forwardStops = List.of();
        }
        if (backwardStops == null) {
            this.backwardStops = List.of();
        }
    }

    public int getTotalStopsCount() {
        return forwardStops.size() + backwardStops.size();
    }

    public int getForwardStopsCount() {
        return forwardStops.size();
    }

    public int getBackwardStopsCount() {
        return backwardStops.size();
    }

    public boolean hasForwardStops() {
        return !forwardStops.isEmpty();
    }

    public boolean hasBackwardStops() {
        return !backwardStops.isEmpty();
    }

    public boolean hasStops() {
        return hasForwardStops() || hasBackwardStops();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof RouteStopsData that)) return false;
        return Objects.equals(forwardStops, that.forwardStops) && Objects.equals(backwardStops, that.backwardStops);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forwardStops, backwardStops);
    }

    @Override
    public String toString() {
        return "RouteStopsData{" +
                "forwardStops=" + forwardStops +
                ", backwardStops=" + backwardStops +
                '}';
    }
}
