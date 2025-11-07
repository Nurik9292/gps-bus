package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.admin;

import biz.ugur.busroutebackend.admin.application.dto.admin.UpdateCommand;
import biz.ugur.busroutebackend.shared.infrastructure.validation.OptionalSize;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

public record AdminUpdateRequest(

    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    @JsonProperty("username")
    String username,

    @Size(max = 100, message = "Full name must not exceed 100 characters")
    @JsonProperty("full_name")
    String fullName,

    @OptionalSize(min = 8, message = "Password must be at least 8 characters")
    @JsonProperty("new_password")
    String newPassword,

    @JsonProperty("is_super_admin")
    Boolean isSuperAdmin,

    @JsonProperty("is_active")
    Boolean isActive,

    @JsonProperty("avatar")
    String avatar
){

   public UpdateCommand toCommand() {
       return new UpdateCommand(username, fullName, newPassword, isSuperAdmin, isActive, avatar);
   }

}
