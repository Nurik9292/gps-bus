package biz.ugur.busroutebackend.banner.appication.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BannerPaginationQuery {
    private int page = 1;
    private int size = 25;
    private String sortField;
    private String sortOrder;
    private Boolean activeOnly;
    private String type;


    public static BannerPaginationQuery create(int page,
                                               int size,
                                               String sortField,
                                               String sortOrder,
                                               Boolean activeOnly) {
        validatePagination(page, size);
        validateSortOrder(sortOrder);

        return builder()
                .page(page)
                .size(size)
                .sortField(sortField)
                .sortOrder(sortOrder)
                .activeOnly(activeOnly)
                .build();
    }

    public static BannerPaginationQuery createWithType(int page,
                                                       int size,
                                                       String sortField,
                                                       String sortOrder,
                                                       Boolean activeOnly,
                                                       String type) {
        validatePagination(page, size);
        validateSortOrder(sortOrder);

        return builder()
                .page(page)
                .size(size)
                .sortField(sortField)
                .sortOrder(sortOrder)
                .activeOnly(activeOnly)
                .type(type)
                .build();
    }

    private static void validatePagination(int page, int size) {
        if (page < 1) {
            throw new IllegalArgumentException("Page number must be greater than 0, got: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Page size must be between 1 and 100, got: " + size);
        }
    }

    private static void validateSortOrder(String sortOrder) {
        if (sortOrder != null && !sortOrder.equalsIgnoreCase("asc") && !sortOrder.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("Sort order must be 'asc' or 'desc', got: " + sortOrder);
        }
    }
}