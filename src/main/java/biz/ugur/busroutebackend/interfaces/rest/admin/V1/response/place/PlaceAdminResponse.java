package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.place;

import biz.ugur.busroutebackend.place.application.dto.AliasResult;
import biz.ugur.busroutebackend.place.application.dto.PlaceResult;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.List;

@Getter
public class PlaceAdminResponse {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("name_en")
    private final String nameEn;

    @JsonProperty("name_tm")
    private final String nameTm;

    @JsonProperty("description")
    private final String description;

    @JsonProperty("address")
    private final String address;

    @JsonProperty("category")
    private final String category;

    @JsonProperty("city_id")
    private final String cityId;

    @JsonProperty("latitude")
    private final BigDecimal latitude;

    @JsonProperty("longitude")
    private final BigDecimal longitude;

    @JsonProperty("is_active")
    private final Boolean isActive;

    @JsonProperty("aliases")
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
        @JsonProperty("id")
        private final String id;
        @JsonProperty("alias")
        private final String alias;
        @JsonProperty("language")
        private final String language;

        public AliasResponse(AliasResult result) {
            this.id = result.id();
            this.alias = result.alias();
            this.language = result.language();
        }
    }
}
