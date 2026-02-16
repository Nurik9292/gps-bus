package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.UpdateStreetCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class StreetUpdateRequest {

    @JsonProperty("name")
    private String name;

    @JsonProperty("name_en")
    private String nameEn;

    @JsonProperty("name_tm")
    private String nameTm;

    @JsonProperty("city_id")
    private String cityId;

    public UpdateStreetCommand toCommand(String id) {
        return new UpdateStreetCommand(id, name, nameEn, nameTm, cityId);
    }
}
