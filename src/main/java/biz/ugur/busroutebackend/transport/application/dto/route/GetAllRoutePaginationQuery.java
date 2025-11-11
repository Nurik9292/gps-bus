package biz.ugur.busroutebackend.transport.application.dto.route;

public record GetAllRoutePaginationQuery(
        Integer page,
        Integer size,
        String sortField,
        String sortOrder,
        Boolean isActivate,
        String query
) {
    public static GetAllRoutePaginationQuery fromParams(
            Integer page,
            Integer size,
            String sortField,
            String sortOrder,
            Boolean isActivate,
            String query) {
        return new GetAllRoutePaginationQuery(
                page != null ? page : 1,
                size != null ? size : 20,
                sortField != null ? sortField : "routeNumber",
                sortOrder != null ? sortOrder : "asc",
                isActivate,
                query
        );
    }
}