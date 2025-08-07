package biz.ugur.busroutebackend.transport.application.dto.stop;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;

import java.math.BigDecimal;

public record UpdateStop(
        String stopId,
        String stopName,
        String nameEn,
        String nameTm,
        String stopCode,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isActive,
        Boolean isMajorStop
) {
    public static UpdateStop fromDomain(BusStop busStop) {
        return new  UpdateStop(
                busStop.getId().getValue(),
                busStop.getStopName(),
                busStop.getNameEn(),
                busStop.getNameTm(),
                busStop.getStopCode(),
                busStop.getLatitude(),
                busStop.getLongitude(),
                busStop.getIsActive(),
                busStop.getIsMajorStop()
        );
    }
}