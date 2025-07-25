package biz.ugur.busroutebackend.admin.application.dto.admin;

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

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("is_super_admin")
    private Boolean isSuperAdmin;

    @JsonProperty("last_login_at")
    private Instant lastLoginAt;

    @JsonProperty("created_at")
    private Instant createdAt;

    public AdminResponse(String id, String username, String fullName, Boolean isActive,
                         Boolean isSuperAdmin, Instant lastLoginAt) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.isActive = isActive;
        this.isSuperAdmin = isSuperAdmin;
        this.lastLoginAt = lastLoginAt;
        this.createdAt = Instant.now();
    }
}