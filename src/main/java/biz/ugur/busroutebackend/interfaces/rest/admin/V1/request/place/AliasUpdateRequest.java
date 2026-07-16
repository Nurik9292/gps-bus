package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.UpdateAliasCommand;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AliasUpdateRequest {

    @NotBlank(message = "Alias is required")
    private String alias;

    private String language;

    public UpdateAliasCommand toCommand(String id) {
        return new UpdateAliasCommand(id, alias, language);
    }
}
