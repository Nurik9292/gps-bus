package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.CreateAliasCommand;
import biz.ugur.busroutebackend.place.application.dto.CreatePlaceCommand;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlaceCreateRequest {

    @NotBlank(message = "Place name is required")
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

    @NotNull(message = "Category is required")
    @JsonProperty("category")
    private String category;

    @NotNull(message = "City ID is required")
    @JsonProperty("city_id")
    private String cityId;

    @NotNull(message = "Latitude is required")
    @JsonProperty("latitude")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    @JsonProperty("longitude")
    private BigDecimal longitude;

    @JsonProperty("is_active")
    private Boolean isActive;

    @JsonProperty("aliases")
    private List<AliasCreateRequest> aliases;

    public CreatePlaceCommand toCommand() {
        List<CreateAliasCommand> aliasCommands = aliases != null
                ? aliases.stream().map(a -> new CreateAliasCommand(a.getAlias(), a.getLanguage())).toList()
                : List.of();
        return new CreatePlaceCommand(name, nameEn, nameTm, description, address, category, cityId, latitude, longitude, isActive, aliasCommands);
    }
}
