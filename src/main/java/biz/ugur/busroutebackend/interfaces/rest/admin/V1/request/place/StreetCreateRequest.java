package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.CreateStreetCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StreetCreateRequest {

    @NotBlank(message = "Street name is required")
    private String name;

    private String nameEn;

    private String nameTm;

    @NotNull(message = "City ID is required")
    private String cityId;

    public CreateStreetCommand toCommand() {
        return new CreateStreetCommand(name, nameEn, nameTm, cityId);
    }
}
