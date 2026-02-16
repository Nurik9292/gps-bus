package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AliasCreateRequest {

    @NotBlank(message = "Alias is required")
    @JsonProperty("alias")
    private String alias;

    @JsonProperty("language")
    private String language = "ru";
}
