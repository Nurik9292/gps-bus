package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.place;

import biz.ugur.busroutebackend.place.application.dto.AliasResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceResult;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class PlaceAdminResponse {

    private final String id;

    private final String name;

    private final String nameEn;

    private final String nameTm;

    private final String description;

    private final String address;

    private final String category;

    private final String cityId;

    private final BigDecimal latitude;

    private final BigDecimal longitude;

    private final Boolean isActive;

    private final List<AliasResponse> aliases;

    public PlaceAdminResponse(PlaceResult result) {
        this.id = result.id();
        this.name = result.name();
        this.nameEn = result.nameEn();
        this.nameTm = result.nameTm();
        this.description = result.description();
        this.address = result.address();
        this.category = result.category();
        this.cityId = result.cityId();
        this.latitude = result.latitude();
        this.longitude = result.longitude();
        this.isActive = result.isActive();
        this.aliases = result.aliases() != null
                ? result.aliases().stream().map(AliasResponse::new).toList()
                : List.of();
    }

    public static PlaceAdminResponse fromResult(PlaceResult result) {
        return new PlaceAdminResponse(result);
    }

    @Getter
    public static class AliasResponse {
        private final String id;
        private final String alias;
        private final String language;

        public AliasResponse(AliasResult result) {
            this.id = result.id();
            this.alias = result.alias();
            this.language = result.language();
        }
    }
}
