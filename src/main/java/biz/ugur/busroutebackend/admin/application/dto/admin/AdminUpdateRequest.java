package biz.ugur.busroutebackend.admin.application.dto.admin;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUpdateRequest {

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @JsonProperty("full_name")
    private String fullName;

    @Size(min = 8, message = "Password must be at least 8 characters")
    @JsonProperty("new_password")
    private String newPassword;

    @JsonProperty("is_active")
    private Boolean isActive;
}
