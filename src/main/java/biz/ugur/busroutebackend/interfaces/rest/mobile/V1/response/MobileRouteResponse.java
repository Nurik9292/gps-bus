package biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response;


import biz.ugur.busroutebackend.geospatial.domain.valueobjects.Coordinates;
import biz.ugur.busroutebackend.transport.application.dto.RouteStopDTO;
import biz.ugur.busroutebackend.transport.application.dto.route.ResolvedRouteData;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class MobileRouteResponse {
    private String id;
    private String routeNumber;
    private String routeName;
    private String nameTm;
    private String nameEn;
    private String routeColor;
    private String cityId;
    private Boolean isActive;
    private Integer estimatedDurationMinutes;
    private List<Coordinates> backwardGeometry;
    private List<Coordinates> forwardGeometry;
    private List<String> forwardStops;
    private List<String> backwardStops;
    private Boolean isFavourite;

    private Boolean isAlternative;
    private String originalRouteId;
    private String originalRouteNumber;

    public static MobileRouteResponse from(RouteData routeData, Boolean isFavorite) {
        return MobileRouteResponse.builder()
                .id(routeData.id())
                .routeName(routeData.routeName())
                .nameTm(routeData.nameTm())
                .nameEn(routeData.nameEn())
                .routeNumber(routeData.routeNumber())
                .routeColor(routeData.routeColor())
                .cityId(routeData.cityId())
                .estimatedDurationMinutes(routeData.estimatedDurationMinutes())
                .isActive(routeData.isActive())
                .backwardGeometry(routeData.backwardGeometry())
                .forwardGeometry(routeData.forwardGeometry())
                .forwardStops(routeData.forwardStops().stream().map(RouteStopDTO::getStopId).collect(Collectors.toList()))
                .backwardStops(routeData.backwardStops().stream().map(RouteStopDTO::getStopId).collect(Collectors.toList()))
                .isFavourite(isFavorite)
                .isAlternative(false)
                .originalRouteId(null)
                .originalRouteNumber(null)
                .build();
    }


    public static MobileRouteResponse fromResolved(ResolvedRouteData resolvedData, Boolean isFavorite) {
        RouteData routeData = resolvedData.routeData();
        return MobileRouteResponse.builder()
                .id(routeData.id())
                .routeName(routeData.routeName())
                .nameTm(routeData.nameTm())
                .nameEn(routeData.nameEn())
                .routeNumber(routeData.routeNumber())
                .routeColor(routeData.routeColor())
                .cityId(routeData.cityId())
                .estimatedDurationMinutes(routeData.estimatedDurationMinutes())
                .isActive(routeData.isActive())
                .backwardGeometry(routeData.backwardGeometry())
                .forwardGeometry(routeData.forwardGeometry())
                .forwardStops(routeData.forwardStops().stream().map(RouteStopDTO::getStopId).collect(Collectors.toList()))
                .backwardStops(routeData.backwardStops().stream().map(RouteStopDTO::getStopId).collect(Collectors.toList()))
                .isFavourite(isFavorite)
                .isAlternative(resolvedData.isAlternative())
                .originalRouteId(resolvedData.originalRouteId())
                .originalRouteNumber(resolvedData.originalRouteNumber())
                .build();
    }
}
