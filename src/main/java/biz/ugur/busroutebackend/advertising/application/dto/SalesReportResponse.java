package biz.ugur.busroutebackend.advertising.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record SalesReportResponse(
        @JsonProperty("items")      List<SalesReportItem> items,
        @JsonProperty("totals")     SalesReportTotals totals,
        @JsonProperty("pagination") Pagination pagination
) {
    public record Pagination(
            @JsonProperty("page")        int page,
            @JsonProperty("size")        int size,
            @JsonProperty("total_pages") int totalPages,
            @JsonProperty("total_items") long totalItems
    ) {}

    public static SalesReportResponse of(
            List<SalesReportItem> items, long totalCount,
            SalesReportTotals totals, int page, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalCount / size);
        return new SalesReportResponse(
                items, totals,
                new Pagination(page, size, totalPages, totalCount));
    }
}
