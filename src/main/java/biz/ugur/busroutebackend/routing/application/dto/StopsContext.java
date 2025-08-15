package biz.ugur.busroutebackend.routing.application.dto;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;

import java.util.List;

public record StopsContext(List<BusStop> fromStops, List<BusStop> toStops) {
    public boolean hasInsufficientStops() {
        return fromStops.isEmpty() || toStops.isEmpty();
    }
}
