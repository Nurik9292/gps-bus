package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin;

import biz.ugur.busroutebackend.admin.domain.model.Admin;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record AdminProfileResponse(
        String id,
        String username,
        @JsonProperty("full_name")
        String fullName,
        @JsonProperty("is_super_admin")
        Boolean isSuperAdmin,
        @JsonProperty("is_active")
        Boolean isActive,
        @JsonProperty("last_login_at")
        Instant lastLoginAt,
        @JsonProperty("created_at")
        Instant createdAt,
        @JsonProperty("updated_at")
        Instant updatedAt,
        @JsonProperty("avatar")
        String avatar
) {

        public static AdminProfileResponse fromDomain(Admin admin) {
                return new AdminProfileResponse(
                        admin.getId().getValue(),
                        admin.getUsername(),
                        admin.getFullName(),
                        admin.getIsSuperAdmin(),
                        admin.getIsActive(),
                        admin.getLastLoginAt(),
                        admin.getCreatedAt(),
                        admin.getUpdatedAt(),
                        admin.getAvatar()
                );
        }
}