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
public final class StreetList implements PagedList<StreetResult> {

    private final List<StreetResult> streets;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public StreetList(List<StreetResult> streets, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.streets = Collections.unmodifiableList(streets);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    @Override
    public List<StreetResult> items() {
        return streets;
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
