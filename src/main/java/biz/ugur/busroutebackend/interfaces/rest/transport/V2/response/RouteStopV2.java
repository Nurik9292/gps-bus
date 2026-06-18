package biz.ugur.busroutebackend.interfaces.rest.transport.V2.response;

import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;

import java.math.BigDecimal;

public record RouteStopV2(
        String id,
        String name,
        Integer sequence,
        Location location,
        Integer distanceFromStartMeters,
        Boolean isMajorStop
) {

    public record Location(BigDecimal lat, BigDecimal lon) {}

    public static RouteStopV2 fromDto(RouteStopDTO stop) {
        return new RouteStopV2(
                stop.getStopId(),
                stop.getStopName(),
                stop.getStopSequence(),
                new Location(stop.getLatitude(), stop.getLongitude()),
                stop.getDistanceFromStartMeters(),
                stop.getIsMajorStop()
        );
    }
}
