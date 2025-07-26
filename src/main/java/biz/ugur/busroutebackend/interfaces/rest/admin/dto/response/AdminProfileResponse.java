package biz.ugur.busroutebackend.interfaces.rest.admin.dto.response;

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
        Instant lastLoginAt
) {}