package biz.ugur.busroutebackend.place.application.dto;

import lombok.Getter;

@Getter
public class GetAllPlacesInput {

    private final int page;
    private final int size;
    private final String sort;
    private final String order;
    private final String cityId;
    private final String category;
    private final String search;

    private GetAllPlacesInput(int page, int size, String sort, String order, String cityId, String category, String search) {
        this.page = page;
        this.size = size;
        this.sort = sort;
        this.order = order;
        this.cityId = cityId;
        this.category = category;
        this.search = search;
    }

    public static GetAllPlacesInput fromParams(int page, int size, String sort, String order,
                                                String cityId, String category, String search) {
        return new GetAllPlacesInput(page, size, sort, order, cityId, category, search);
    }
}
