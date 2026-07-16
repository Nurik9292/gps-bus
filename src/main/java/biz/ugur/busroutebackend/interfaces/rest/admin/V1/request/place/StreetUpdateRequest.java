package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.UpdateStreetCommand;
import lombok.Data;

@Data
public class StreetUpdateRequest {

    private String name;

    private String nameEn;

    private String nameTm;

    private String cityId;

    public UpdateStreetCommand toCommand(String id) {
        return new UpdateStreetCommand(id, name, nameEn, nameTm, cityId);
    }
}
