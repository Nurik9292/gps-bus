package biz.ugur.busroutebackend.banner.application.dto;

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
public final class BannerList implements PagedList<BannerResponse> {

    private final List<BannerResponse> banners;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public BannerList(List<BannerResponse> banners, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.banners = Collections.unmodifiableList(banners);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static BannerList of(
        List<BannerResponse> banners,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new BannerList(banners, activeCount, currentPage, pageSize, totalItems);
    }


    @Override
    public List<BannerResponse> items() {
        return banners;
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
