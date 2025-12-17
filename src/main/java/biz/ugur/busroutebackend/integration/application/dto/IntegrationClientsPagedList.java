package biz.ugur.busroutebackend.integration.application.dto;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.Builder;

import java.util.List;


@Builder
public record IntegrationClientsPagedList(
        List<IntegrationClientDTO> items,
        Long activeCount,
        PaginationInfo pagination
) implements PagedList<IntegrationClientDTO> {

    public static IntegrationClientsPagedList of(List<IntegrationClientDTO> items,
                                                  long totalCount,
                                                  int page,
                                                  int size) {
        return IntegrationClientsPagedList.builder()
                .items(items)
                .activeCount(totalCount)
                .pagination(PaginationInfo.of(page, size, totalCount))
                .build();
    }
}
