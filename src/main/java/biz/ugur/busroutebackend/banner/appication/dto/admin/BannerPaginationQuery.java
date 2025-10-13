package biz.ugur.busroutebackend.banner.appication.dto.admin;

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
       return builder()
               .page(page)
               .size(size)
               .sortField(sortField)
               .sortOrder(sortOrder)
               .build();
    }

    public static BannerPaginationQuery createWithType(int page,
                                                       int size,
                                                       String sortField,
                                                       String sortOrder,
                                                       Boolean activeOnly,
                                                       String type) {
        return builder()
                .page(page)
                .size(size)
                .sortField(sortField)
                .sortOrder(sortOrder)
                .activeOnly(activeOnly)
                .type(type)
                .build();
    }
}