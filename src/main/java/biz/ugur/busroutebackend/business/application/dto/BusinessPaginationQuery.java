package biz.ugur.busroutebackend.business.application.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BusinessPaginationQuery {

    private int page;
    private int size;
    private String sortField;
    private String sortOrder;
    private String status;   // filter by BusinessStatus (optional)
    private String type;     // filter by BusinessType   (optional)

    public static BusinessPaginationQuery create(int page, int size,
                                                  String sortField, String sortOrder,
                                                  String status, String type) {
        validatePagination(page, size);
        validateSortOrder(sortOrder);
        return builder()
                .page(page).size(size)
                .sortField(sortField)
                .sortOrder(sortOrder)
                .status(status)
                .type(type)
                .build();
    }

    private static void validatePagination(int page, int size) {
        if (page < 1) throw new IllegalArgumentException("Page must be >= 1, got: " + page);
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("Size must be between 1 and 100, got: " + size);
        }
    }

    private static void validateSortOrder(String sortOrder) {
        if (sortOrder != null
                && !sortOrder.equalsIgnoreCase("asc")
                && !sortOrder.equalsIgnoreCase("desc")) {
            throw new IllegalArgumentException("sortOrder must be 'asc' or 'desc', got: " + sortOrder);
        }
    }
}
