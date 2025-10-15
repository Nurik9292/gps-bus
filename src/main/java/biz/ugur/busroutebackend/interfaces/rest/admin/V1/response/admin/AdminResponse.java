package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.AdminResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.Instant;

@Data
public class AdminResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("username")
    private String username;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("avatar")
    private String avatar;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("is_super_admin")
    private Boolean isSuperAdmin;

    @JsonProperty("last_login_at")
    private Instant lastLoginAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    public AdminResponse(
            String id,
            String username,
            String fullName,
            String avatar,
            Boolean isActive,
            Boolean isSuperAdmin,
            Instant lastLoginAt,
            Instant createdAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.avatar = avatar;
        this.isActive = isActive;
        this.isSuperAdmin = isSuperAdmin;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = createdAt;
    }

    public AdminResponse(
            String id,
            String username,
            String fullName,
            Boolean isActive,
            Boolean isSuperAdmin,
            Instant lastLoginAt) {
        this(id, username, fullName, null, isActive, isSuperAdmin, lastLoginAt, Instant.now());
    }

    public static AdminResponse fromResult(AdminResult result) {
        return new AdminResponse(
                result.id(),
                result.username(),
                result.fullName(),
                result.avatar(),
                result.isActive(),
                result.isSuperAdmin(),
                result.lastLoginAt(),
                result.createdAt()
        );
    }
}