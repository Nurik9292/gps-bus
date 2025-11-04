package biz.ugur.busroutebackend.admin.application.dto.city;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

/**
 * DTO for city list results with pagination support.
 * Part of the Admin bounded context application layer.
 *
 * <p>Following DDD and Clean Architecture principles:
 * <ul>
 *   <li>Application layer DTO (not domain model)</li>
 *   <li>Contains pagination metadata via PaginationInfo value object</li>
 *   <li>Immutable for thread safety</li>
 *   <li>Implements PagedList interface for standardized pagination</li>
 * </ul>
 */
@ToString
@Getter
@EqualsAndHashCode
public final class CityList implements PagedList<CityResult> {

    private final List<CityResult> cities;
    private final Long activeCount;
    private final PaginationInfo pagination;

    /**
     * Constructor for paginated city list.
     *
     * @param cities List of city data for current page
     * @param activeCount Total number of active cities
     * @param currentPage Current page number (1-based)
     * @param pageSize Number of items per page
     * @param totalItems Total number of items across all pages
     */
    public CityList(List<CityResult> cities, Long activeCount, int currentPage, int pageSize, long totalItems) {
        this.cities = Collections.unmodifiableList(cities);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    /**
     * Factory method for creating CityList.
     *
     * @param cities List of city data for current page
     * @param activeCount Total number of active cities
     * @param currentPage Current page number (1-based)
     * @param pageSize Number of items per page
     * @param totalItems Total number of items across all pages
     * @return New CityList instance
     */
    public static CityList of(
        List<CityResult> cities,
        Long activeCount,
        int currentPage,
        int pageSize,
        long totalItems
    ) {
        return new CityList(cities, activeCount, currentPage, pageSize, totalItems);
    }

    // PagedList interface implementation

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

    /**
     * @deprecated Use pagination().getTotalItems() instead.
     * Provided for backward compatibility during migration.
     */
    @Deprecated(since = "2025-11-02", forRemoval = true)
    public Integer getTotalCount() {
        return (int) pagination.getTotalItems();
    }
}

