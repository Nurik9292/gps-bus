package biz.ugur.busroutebackend.transport.application.dto.stop;

public record GetAllStopPaginationQuery(
        Integer page,
        Integer size,
        String sortField,
        String sortOrder,
        Boolean isActivate,
        String searchQuery,
        String cityId
) {
    public static GetAllStopPaginationQuery fromParams(
            Integer page,
            Integer size,
            String sortField,
            String sortOrder,
            Boolean isActivate,
            String searchQuery,
            String cityId) {
        return new GetAllStopPaginationQuery(
                page != null ? page : 1,
                size != null ? size : 20,
                sortField != null ? sortField : "createdAt",
                sortOrder != null ? sortOrder : "desc",
                isActivate,
                searchQuery != null && !searchQuery.isBlank() ? searchQuery.trim() : null,
                cityId != null && !cityId.isBlank() ? cityId.trim() : null
        );
    }
}
