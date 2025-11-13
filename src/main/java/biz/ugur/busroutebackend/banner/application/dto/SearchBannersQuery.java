package biz.ugur.busroutebackend.banner.application.dto;

import biz.ugur.busroutebackend.banner.domain.enums.BannerType;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class SearchBannersQuery {

    private BannerType type;

    private Boolean isActive;

    private String titleSearch;

    private Boolean periodActive;

    private Integer minDisplayOrder;

    private Integer maxDisplayOrder;

    private LocalDateTime createdAfter;

    private Integer expiringWithinDays;

    private int page = 1;
    private int size = 25;

    private String sortField;

    private String sortOrder;

    public void validate() {
        validatePagination();
        validateSortOrder();
        validateDisplayOrderRange();
        validateExpiringDays();
    }

    private void validatePagination() {
        if (page < 1) {
            throw new IllegalArgumentException("Page number must be greater than 0, got: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100, got: " + size);
        }
    }

    private void validateSortOrder() {
        if (sortOrder != null && !sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("Sort order must be 'asc' or 'desc', got: " + sortOrder);
        }
    }

    private void validateDisplayOrderRange() {
        if (minDisplayOrder != null && maxDisplayOrder != null && minDisplayOrder > maxDisplayOrder) {
            throw new IllegalArgumentException(
                String.format("minDisplayOrder (%d) cannot be greater than maxDisplayOrder (%d)",
                    minDisplayOrder, maxDisplayOrder)
            );
        }
    }

    private void validateExpiringDays() {
        if (expiringWithinDays != null && expiringWithinDays < 1) {
            throw new IllegalArgumentException("expiringWithinDays must be at least 1, got: " + expiringWithinDays);
        }
    }

    public boolean hasAnyCriteria() {
        return type != null
            || isActive != null
            || titleSearch != null
            || periodActive != null
            || minDisplayOrder != null
            || maxDisplayOrder != null
            || createdAfter != null
            || expiringWithinDays != null;
    }
}
