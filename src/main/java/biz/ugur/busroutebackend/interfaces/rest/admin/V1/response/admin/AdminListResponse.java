package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

/**
 * REST response DTO for admin list with pagination.
 * Part of the Interfaces layer (REST API).
 *
 * Following Clean Architecture:
 * - Interfaces layer DTO (converts from application layer DTOs)
 * - Contains JSON annotations for API contract
 * - Immutable response object
 */
@Getter
@ToString
@EqualsAndHashCode
public class AdminListResponse {

    @JsonProperty("admins")
    private final List<AdminResponse> admins;

    @JsonProperty("active_count")
    private final Long activeCount;

    @JsonProperty("pagination")
    private final PaginationInfo pagination;

    /**
     * Constructor with pagination support.
     */
    public AdminListResponse(List<AdminResponse> admins, Long activeCount, PaginationInfo pagination) {
        this.admins = admins;
        this.activeCount = activeCount;
        this.pagination = pagination;
    }

    /**
     * Factory method to create response from application layer DTO.
     *
     * @param result The AdminList DTO from application layer
     * @return AdminListResponse for REST API
     */
    public static AdminListResponse fromResult(AdminList result) {
        return new AdminListResponse(
                result.getAdmins()
                        .stream()
                        .map(AdminResponse::fromResult)
                        .toList(),
                result.getActiveCount(),
                result.getPagination());
    }
}