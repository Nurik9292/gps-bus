package biz.ugur.busroutebackend.admin.application.dto.admin;

public record AdminPaginationQuery(
        int page,
        int size,
        String sortField,
        String sortOrder,
        Boolean isActivate,
        String searchQuery,
        String role
) {

    public static AdminPaginationQuery fromParams(
            int page,
            int size,
            String sortField,
            String sortOrder,
            Boolean isActivate,
            String searchQuery,
            String role) {
        return new AdminPaginationQuery(
                page,
                size,
                sortField != null ? sortField : "created_at",
                sortOrder != null ? sortOrder : "desc",
                isActivate,
                searchQuery != null && !searchQuery.isBlank() ? searchQuery.trim() : null,
                role != null && !role.isBlank() ? role.trim() : null
        );
    }
}
