package biz.ugur.busroutebackend.interfaces.rest.admin.response.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminList;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class AdminListResponse {

    @JsonProperty("admins")
    private List<AdminResponse> admins;

    @JsonProperty("total_count")
    private Integer totalCount;

    @JsonProperty("active_count")
    private Long activeCount;

    public AdminListResponse(List<AdminResponse> admins, Long activeCount) {
        this.admins = admins;
        this.totalCount = admins.size();
        this.activeCount = activeCount;
    }

    public static AdminListResponse fromResult(AdminList result) {
        return new AdminListResponse(
                result.getAdmins()
                        .stream()
                        .map(AdminResponse::fromResult)
                        .toList(),
                result.getActiveCount());
    }
}