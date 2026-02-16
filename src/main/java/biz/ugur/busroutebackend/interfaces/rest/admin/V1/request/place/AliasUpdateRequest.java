package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.UpdateAliasCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AliasUpdateRequest {

    @NotBlank(message = "Alias is required")
    @JsonProperty("alias")
    private String alias;

    @JsonProperty("language")
    private String language;

    public UpdateAliasCommand toCommand(String id) {
        return new UpdateAliasCommand(id, alias, language);
    }
}
