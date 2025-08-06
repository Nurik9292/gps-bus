package biz.ugur.busroutebackend.interfaces.rest.admin.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUpdateProfileRequest {

    @JsonProperty("username")
    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String username;

    @JsonProperty("full_name")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @Size(min = 8, message = "Password must be at least 8 characters")
    private String newPassword;

}
