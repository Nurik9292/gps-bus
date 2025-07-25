package biz.ugur.busroutebackend.admin.application.dto.city;

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
}
