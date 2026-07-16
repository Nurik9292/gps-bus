package biz.ugur.busroutebackend.catalogsearch.application.dto;

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
public final class NamesList implements PagedList<CatalogNameResult> {

    private final List<CatalogNameResult> names;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public NamesList(List<CatalogNameResult> names, int currentPage, int pageSize, long totalItems) {
        this.names = Collections.unmodifiableList(names);
        this.activeCount = totalItems;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    @Override
    public List<CatalogNameResult> items() {
        return names;
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
