package biz.ugur.busroutebackend.interfaces.rest.transport.V2.response;

import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;

import java.math.BigDecimal;

public record StopV2(
        String id,
        String name,
        String nameEn,
        String nameTm,
        String code,
        Location location,
        Boolean isMajorStop,
        String cityId
) {

    public record Location(BigDecimal lat, BigDecimal lon) {}

    public static StopV2 fromStopData(StopData stop) {
        return new StopV2(
                stop.id(),
                stop.stopName(),
                stop.nameEn(),
                stop.nameTm(),
                stop.stopCode(),
                new Location(stop.latitude(), stop.longitude()),
                stop.isMajorStop(),
                stop.cityId()
        );
    }
}
