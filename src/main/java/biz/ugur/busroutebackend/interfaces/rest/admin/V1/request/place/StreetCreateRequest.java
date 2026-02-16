package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.CreateStreetCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StreetCreateRequest {

    @NotBlank(message = "Street name is required")
    @JsonProperty("name")
    private String name;

    @JsonProperty("name_en")
    private String nameEn;

    @JsonProperty("name_tm")
    private String nameTm;

    @NotNull(message = "City ID is required")
    @JsonProperty("city_id")
    private String cityId;

    public CreateStreetCommand toCommand() {
        return new CreateStreetCommand(name, nameEn, nameTm, cityId);
    }
}
