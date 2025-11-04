package biz.ugur.busroutebackend.transport.application.dto.stop;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

/**
 * DTO for bus stop list results with pagination support.
 * Part of the Transport bounded context application layer.
 *
 * <p>Following DDD and Clean Architecture principles:
 * <ul>
 *   <li>Application layer DTO (not domain model)</li>
 *   <li>Contains pagination metadata via PaginationInfo value object</li>
 *   <li>Immutable for thread safety</li>
 *   <li>Implements PagedList interface for standardized pagination</li>
 * </ul>
 */
@Getter
@ToString
@EqualsAndHashCode
public final class StopList implements PagedList<StopData> {

    private final List<StopData> stops;
    private final Long activeCount;
    private final PaginationInfo pagination;

    /**
     * Constructor for paginated stop list.
     *
     * @param stops List of stop data for current page
     * @param activeCount Total number of active stops
     * @param currentPage Current page number (1-based)
     * @param pageSize Number of items per page
     * @param totalItems Total number of items across all pages
     */
    public StopList(List<StopData> stops, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.stops = Collections.unmodifiableList(stops);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    /**
     * Factory method for creating StopList.
     *
     * @param stops List of stop data for current page
     * @param activeCount Total number of active stops
     * @param currentPage Current page number (1-based)
     * @param pageSize Number of items per page
     * @param totalItems Total number of items across all pages
     * @return New StopList instance
     */
    public static StopList of(
        List<StopData> stops,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new StopList(stops, activeCount, currentPage, pageSize, totalItems);
    }

    // PagedList interface implementation

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
