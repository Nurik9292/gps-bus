package biz.ugur.busroutebackend.transport.application.dto.stop;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;

import java.math.BigDecimal;
import java.time.Instant;

public record StopData(
        String id,
        String stopName,
        String nameEn,
        String nameTm,
        String stopCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isActive,
        Boolean isMajorStop,
        Integer servingRouteCount,
        Instant createdAt,
        Instant updatedAt,
        String cityId

) {
    public static StopData fromDomain(BusStop stop) {
        return new StopData(
                stop.getId().getValue(),
                stop.getStopName(),
                stop.getNameEn(),
                stop.getNameTm(),
                stop.getStopCode().getValue(),
                stop.getLatitude(),
                stop.getLongitude(),
                stop.getIsActive(),
                stop.getIsMajorStop(),
                stop.getServingRoutesCount(),
                stop.getCreatedAt(),
                stop.getUpdatedAt(),
                stop.getCityId()
        );
    }
}
