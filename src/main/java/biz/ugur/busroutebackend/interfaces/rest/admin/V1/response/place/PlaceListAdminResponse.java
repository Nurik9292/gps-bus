package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.place;

import biz.ugur.busroutebackend.place.application.dto.PlaceList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.Getter;

import java.util.List;

@Getter
public class PlaceListAdminResponse {

    private final List<PlaceAdminResponse> places;

    private final Long activeCount;

    private final PaginationInfo pagination;

    public PlaceListAdminResponse(List<PlaceAdminResponse> places, Long activeCount, PaginationInfo pagination) {
        this.places = places;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

    public static PlaceListAdminResponse fromResult(PlaceList placeList) {
        return new PlaceListAdminResponse(
                placeList.getPlaces().stream().map(PlaceAdminResponse::fromResult).toList(),
                placeList.getActiveCount(),
                placeList.getPagination()
        );
    }
}
