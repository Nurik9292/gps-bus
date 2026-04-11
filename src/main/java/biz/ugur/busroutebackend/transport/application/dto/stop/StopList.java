package biz.ugur.busroutebackend.transport.application.dto.stop;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@Getter
@ToString
@EqualsAndHashCode
public final class StopList implements PagedList<StopData> {

    private final List<StopData> stops;
    private final Long activeCount;
    private final PaginationInfo pagination;

   
    public StopList(List<StopData> stops, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.stops = Collections.unmodifiableList(stops);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

 
    public static StopList of(
        List<StopData> stops,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new StopList(stops, activeCount, currentPage, pageSize, totalItems);
    }


    @Override
    public List<StopData> items() {
        return stops;
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
