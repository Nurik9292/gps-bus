package biz.ugur.busroutebackend.admin.application.dto.city;

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
public final class CityList implements PagedList<CityResult> {

    private final List<CityResult> cities;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public CityList(List<CityResult> cities, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.cities = Collections.unmodifiableList(cities);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static CityList of(
        List<CityResult> cities,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new CityList(cities, activeCount, currentPage, pageSize, totalItems);
    }


    @Override
    public List<CityResult> items() {
        return cities;
    }

    @Override
    public Long activeCount() {
        return activeCount;
    }

    @Override
    public PaginationInfo pagination() {
        return pagination;
    }

    @Deprecated(since = "2025-11-02", forRemoval = true)
    public Integer getTotalCount() {
        return (int) pagination.getTotalItems();
    }
}

