package biz.ugur.busroutebackend.shared.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
public final class PaginationInfo {

    @JsonProperty("current_page")
    private final int currentPage;

    @JsonProperty("page_size")
    private final int pageSize;

    @JsonProperty("total_items")
    private final long totalItems;

    @JsonProperty("total_pages")
    private final int totalPages;

    private PaginationInfo(int currentPage, int pageSize, long totalItems) {
        validateInputs(currentPage, pageSize, totalItems);

        this.currentPage = currentPage;
        this.pageSize = pageSize;
        this.totalItems = totalItems;
        this.totalPages = calculateTotalPages(totalItems, pageSize);
    }

    public static PaginationInfo of(int currentPage, int pageSize, long totalItems) {
        return new PaginationInfo(currentPage, pageSize, totalItems);
    }

    private void validateInputs(int currentPage, int pageSize, long totalItems) {
        if (currentPage < 1) {
            throw new IllegalArgumentException("Current page must be greater than 0, got: " + currentPage);
        }
        if (pageSize < 1) {
            throw new IllegalArgumentException("Page size must be greater than 0, got: " + pageSize);
        }
        if (totalItems < 0) {
            throw new IllegalArgumentException("Total items cannot be negative, got: " + totalItems);
        }
    }

    private int calculateTotalPages(long totalItems, int pageSize) {
        if (totalItems == 0) {
            return 0;
        }
        return (int) Math.ceil((double) totalItems / pageSize);
    }

    public boolean hasNext() {
        return currentPage < totalPages;
    }

    public boolean hasPrevious() {
        return currentPage > 1;
    }

    public boolean isFirst() {
        return currentPage == 1;
    }

    public boolean isLast() {
        return currentPage == totalPages || totalPages == 0;
    }
}
