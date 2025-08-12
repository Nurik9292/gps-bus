package biz.ugur.busroutebackend.admin.application.dto.banner;

import lombok.Data;

@Data
public class BannerPaginationQuery {
    private int page = 1;
    private int size = 25;
    private String sortField;
    private String sortOrder;
    private Boolean activeOnly;

    public BannerPaginationQuery(int page, int size, String sortField, String sortOrder, Boolean activeOnly) {
        this.page = Math.max(1, page);
        this.size = Math.min(100, Math.max(1, size));
        this.sortField = sortField != null ? sortField : "display_order";
        this.sortOrder = sortOrder != null ? sortOrder : "asc";
        this.activeOnly = activeOnly;
    }
}