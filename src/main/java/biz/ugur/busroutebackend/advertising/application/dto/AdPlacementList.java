package biz.ugur.busroutebackend.advertising.application.dto;

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
public final class AdPlacementList implements PagedList<AdPlacementResponse> {

    private final List<AdPlacementResponse> placements;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public AdPlacementList(List<AdPlacementResponse> placements, Long activeCount,
                            int currentPage, int pageSize, long totalItems) {
        this.placements = Collections.unmodifiableList(placements);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static AdPlacementList of(List<AdPlacementResponse> placements, Long activeCount,
                                      int currentPage, int pageSize, long totalItems) {
        return new AdPlacementList(placements, activeCount, currentPage, pageSize, totalItems);
    }

    @Override public List<AdPlacementResponse> items() { return placements; }
    @Override public Long activeCount() { return activeCount; }
    @Override public PaginationInfo pagination() { return pagination; }
}
