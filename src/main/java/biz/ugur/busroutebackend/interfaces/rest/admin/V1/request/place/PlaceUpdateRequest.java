package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.UpdatePlaceCommand;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PlaceUpdateRequest {

    private String name;

    private String nameEn;

    private String nameTm;

    private String description;

    private String address;

    private String category;

    private String cityId;

    private BigDecimal latitude;

    private BigDecimal longitude;

    public UpdatePlaceCommand toCommand(String id) {
        return new UpdatePlaceCommand(id, name, nameEn, nameTm, description, address, category, cityId, latitude, longitude);
    }
}
