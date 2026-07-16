package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AliasCreateRequest {

    @NotBlank(message = "Alias is required")
    private String alias;

    private String language = "ru";
}
