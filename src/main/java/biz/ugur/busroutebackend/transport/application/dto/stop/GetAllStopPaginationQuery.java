package biz.ugur.busroutebackend.transport.application.dto.stop;

public record GetAllStopPaginationQuery(
        Integer page,
        Integer size,
        String sortField,
        String sortOrder,
        Boolean isActivate
) {
    public static GetAllStopPaginationQuery fromParams(
            Integer page,
            Integer size,
            String sortField,
            String sortOrder,
            Boolean isActivate) {
        return new GetAllStopPaginationQuery(
                page != null ? page : 1,
                size != null ? size : 20,
                sortField != null ? sortField : "stopName",
                sortOrder != null ? sortOrder : "asc",
                isActivate
        );
    }
}
