package biz.ugur.busroutebackend.interfaces.rest.admin.request;

import biz.ugur.busroutebackend.admin.application.dto.admin.CreateCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminCreateRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
        @JsonProperty("username")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters")
        @JsonProperty("password")
        String password,

        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must not exceed 100 characters")
        @JsonProperty("full_name")
        String fullName,

        @JsonProperty("is_super_admin")
        Boolean isSuperAdmin,

        @JsonProperty("is_active")
        Boolean isActive
) {

    public CreateCommand toCommand() {
        return new CreateCommand(
                username,
                fullName,
                password,
                isSuperAdmin,
                isActive
        );
    }
}