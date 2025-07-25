package biz.ugur.busroutebackend.admin.application.dto.city;

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
}

