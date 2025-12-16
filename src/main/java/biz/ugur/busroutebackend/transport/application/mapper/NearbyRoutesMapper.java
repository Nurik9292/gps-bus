package biz.ugur.busroutebackend.transport.application.mapper;

import biz.ugur.busroutebackend.interfaces.rest.mobile.V1.response.NearbyRoutesResponse;
import biz.ugur.busroutebackend.transport.domain.valueobject.NearbyRouteInfo;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NearbyRoutesMapper {

    public NearbyRoutesResponse toResponse(List<NearbyRouteInfo> routes) {
        List<NearbyRoutesResponse.NearbyRouteItem> items = routes.stream()
                .map(this::toRouteItem)
                .toList();

        return NearbyRoutesResponse.builder()
                .routes(items)
                .totalCount(items.size())
                .build();
    }

    public NearbyRoutesResponse.NearbyRouteItem toRouteItem(NearbyRouteInfo info) {
        return NearbyRoutesResponse.NearbyRouteItem.builder()
                .id(info.getRouteId())
                .routeNumber(info.getRouteNumber())
                .routeColor(info.getRouteColor())
                .build();
    }
}
