package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.city;

import biz.ugur.busroutebackend.admin.application.dto.city.CityResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class CityResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("name_tm")
    private String nameTm;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("display_order")
    private Integer displayOrder;

    public CityResponse(String id, String name, String nameTm, Boolean isActive, Integer displayOrder) {
        this.id = id;
        this.name = name;
        this.nameTm = nameTm;
        this.isActive = isActive;
        this.displayOrder = displayOrder;
    }

    public static CityResponse fromResult(CityResult result) {
        return new CityResponse(
                result.id(),
                result.name(),
                result.nameTm(),
                result.isActive(),
                result.displayOrder()
        );
    }
}

