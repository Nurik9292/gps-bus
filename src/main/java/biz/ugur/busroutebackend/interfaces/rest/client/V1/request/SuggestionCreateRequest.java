package biz.ugur.busroutebackend.interfaces.rest.client.V1.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SuggestionCreateRequest {

    @NotNull(message = "Entity type is required")
    private String entityType;

    @NotBlank(message = "Entity ID is required")
    private String entityId;

    @NotBlank(message = "Suggested alias is required")
    private String suggestedAlias;

    private String language;
}
