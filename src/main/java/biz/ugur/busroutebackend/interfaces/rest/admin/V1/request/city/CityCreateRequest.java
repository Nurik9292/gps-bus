package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CreateCity;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CityCreateRequest {

    @NotBlank(message = "City name is required")
    @Size(max = 100, message = "City name must not exceed 100 characters")
    @JsonProperty("name")
    private String name;

    @Size(max = 100, message = "Turkmen name must not exceed 100 characters")
    @JsonProperty("name_tm")
    private String nameTm;

    @JsonProperty("display_order")
    private Integer displayOrder = 0;

    public CreateCity toCommand() {
        return new CreateCity(this.name, this.nameTm, this.displayOrder);
    }
}
