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
public final class AdTariffList implements PagedList<AdTariffResponse> {

    private final List<AdTariffResponse> tariffs;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public AdTariffList(List<AdTariffResponse> tariffs, Long activeCount,
                         int currentPage, int pageSize, long totalItems) {
        this.tariffs = Collections.unmodifiableList(tariffs);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static AdTariffList of(List<AdTariffResponse> tariffs, Long activeCount,
                                   int currentPage, int pageSize, long totalItems) {
        return new AdTariffList(tariffs, activeCount, currentPage, pageSize, totalItems);
    }

    @Override public List<AdTariffResponse> items() { return tariffs; }
    @Override public Long activeCount() { return activeCount; }
    @Override public PaginationInfo pagination() { return pagination; }
}
