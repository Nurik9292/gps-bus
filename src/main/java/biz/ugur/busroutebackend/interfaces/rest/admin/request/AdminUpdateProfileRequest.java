package biz.ugur.busroutebackend.interfaces.rest.admin.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminUpdateProfileRequest {
    @JsonProperty("full_name")
    @Size(max = 100, message = "Full name must not exceed 100 characters")
    private String fullName;

    @JsonProperty("avatar")
    private String avatar;
}
