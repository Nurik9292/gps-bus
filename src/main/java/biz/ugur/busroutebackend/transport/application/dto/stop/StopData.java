package biz.ugur.busroutebackend.transport.application.dto.stop;

import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String cityId,


        @JsonInclude(JsonInclude.Include.NON_NULL)
        double[] coordinates

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
                stop.getCityId(),
                stop.toCoordinates().toGeoJson()
        );
    }


    public static StopData fromDomainWithoutGeoJson(BusStop stop) {
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
                stop.getCityId(),
                null
        );
    }
}
