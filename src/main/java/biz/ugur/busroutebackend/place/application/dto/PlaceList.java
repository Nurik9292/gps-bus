package biz.ugur.busroutebackend.place.application.dto;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@ToString
@Getter
@EqualsAndHashCode
public final class PlaceList implements PagedList<PlaceResult> {

    private final List<PlaceResult> places;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public PlaceList(List<PlaceResult> places, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.places = Collections.unmodifiableList(places);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    @Override
    public List<PlaceResult> items() {
        return places;
    }

    @Override
    public Long activeCount() {
        return activeCount;
    }

    @Override
    public PaginationInfo pagination() {
        return pagination;
    }
}
