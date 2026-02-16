package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.UpdatePlaceCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlaceUpdateRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("name_en")
    private String nameEn;

    @JsonProperty("name_tm")
    private String nameTm;

    @JsonProperty("description")
    private String description;

    @JsonProperty("address")
    private String address;

    @JsonProperty("category")
    private String category;

    @JsonProperty("city_id")
    private String cityId;

    @JsonProperty("latitude")
    private BigDecimal latitude;

    @JsonProperty("longitude")
    private BigDecimal longitude;

    public UpdatePlaceCommand toCommand(String id) {
        return new UpdatePlaceCommand(id, name, nameEn, nameTm, description, address, category, cityId, latitude, longitude);
    }
}
