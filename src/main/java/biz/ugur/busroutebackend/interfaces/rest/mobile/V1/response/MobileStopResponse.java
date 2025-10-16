package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;

import biz.ugur.busroutebackend.transport.application.dto.BusArrivalInfo;
import biz.ugur.busroutebackend.transport.application.dto.stop.StopData;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MobileStopResponse {
    private String stopId;
    private String stopName;
    private String nameEn;
    private String nameTm;
    private String stopCode;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Boolean isActive;
    private Boolean isMajorStop;
    private Integer servingRouteCount;
    private String cityId;
    private Boolean isFavourite;
    private List<String> forwardRouteIds;
    private List<String> backwardRouteIds;
    private List<BusArrivalInfo> busArrivalInfos;


    public static MobileStopResponse from(StopData stopResult, Boolean isFavorite) {
        return from(stopResult, isFavorite, List.of(), List.of(), List.of());
    }


    public static MobileStopResponse from(StopData stopResult,
                                          Boolean isFavorite,
                                          List<String> forwardRouteIds,
                                          List<String> backwardRouteIds) {
        return MobileStopResponse.builder()
                .stopId(stopResult.id())
                .stopName(stopResult.stopName())
                .stopCode(stopResult.stopCode())
                .nameTm(stopResult.nameTm())
                .nameEn(stopResult.nameEn())
                .cityId(stopResult.cityId())
                .isMajorStop(stopResult.isMajorStop())
                .isActive(stopResult.isActive())
                .servingRouteCount(stopResult.servingRouteCount())
                .latitude(stopResult.latitude())
                .longitude(stopResult.longitude())
                .isFavourite(isFavorite)
                .forwardRouteIds(forwardRouteIds)
                .backwardRouteIds(backwardRouteIds)
                .busArrivalInfos(List.of())
                .build();
    }


    public static MobileStopResponse from(StopData stopResult,
                                          Boolean isFavorite,
                                          List<String> forwardRouteIds,
                                          List<String> backwardRouteIds,
                                          List<BusArrivalInfo> busArrivalInfos) {
        return MobileStopResponse.builder()
                .stopId(stopResult.id())
                .stopName(stopResult.stopName())
                .stopCode(stopResult.stopCode())
                .nameTm(stopResult.nameTm())
                .nameEn(stopResult.nameEn())
                .cityId(stopResult.cityId())
                .isMajorStop(stopResult.isMajorStop())
                .isActive(stopResult.isActive())
                .servingRouteCount(stopResult.servingRouteCount())
                .latitude(stopResult.latitude())
                .longitude(stopResult.longitude())
                .isFavourite(isFavorite)
                .forwardRouteIds(forwardRouteIds)
                .backwardRouteIds(backwardRouteIds)
                .busArrivalInfos(busArrivalInfos)
                .build();
    }
}