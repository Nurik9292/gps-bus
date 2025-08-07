package biz.ugur.busroutebackend.interfaces.rest.admin.request.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityUpdate;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;


public record CityUpdateRequest(

        @NotBlank(message = "City name is required")
        @Size(min = 2, max = 100, message = "City name must be between 2 and 100 characters")
        String name,

        @Size(max = 100, message = "Turkmen name must not exceed 100 characters")
        String nameTm,

        Boolean isActive,

        Integer displayOrder

) {

    public CityUpdate toCommand(String id) {
        return new CityUpdate(id, name, nameTm, isActive, displayOrder);
    }

}
