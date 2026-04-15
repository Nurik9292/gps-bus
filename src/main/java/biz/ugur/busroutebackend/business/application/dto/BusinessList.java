package biz.ugur.busroutebackend.business.application.dto;

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
public final class BusinessList implements PagedList<BusinessResponse> {

    private final List<BusinessResponse> businesses;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public BusinessList(List<BusinessResponse> businesses, Long activeCount,
                         int currentPage, int pageSize, long totalItems) {
        this.businesses = Collections.unmodifiableList(businesses);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static BusinessList of(List<BusinessResponse> businesses, Long activeCount,
                                   int currentPage, int pageSize, long totalItems) {
        return new BusinessList(businesses, activeCount, currentPage, pageSize, totalItems);
    }

    @Override public List<BusinessResponse> items() { return businesses; }
    @Override public Long activeCount() { return activeCount; }
    @Override public PaginationInfo pagination() { return pagination; }
}
