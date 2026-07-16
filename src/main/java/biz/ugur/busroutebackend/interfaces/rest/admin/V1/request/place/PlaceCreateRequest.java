package biz.ugur.busroutebackend.interfaces.rest.admin.V1.request.place;

import biz.ugur.busroutebackend.place.application.dto.CreateAliasCommand;
import biz.ugur.busroutebackend.place.application.dto.CreatePlaceCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PlaceCreateRequest {

    @NotBlank(message = "Place name is required")
    private String name;

    private String nameEn;

    private String nameTm;

    private String description;

    private String address;

    @NotNull(message = "Category is required")
    private String category;

    @NotNull(message = "City ID is required")
    private String cityId;

    @NotNull(message = "Latitude is required")
    private BigDecimal latitude;

    @NotNull(message = "Longitude is required")
    private BigDecimal longitude;

    private Boolean isActive;

    private List<AliasCreateRequest> aliases;

    public CreatePlaceCommand toCommand() {
        List<CreateAliasCommand> aliasCommands = aliases != null
                ? aliases.stream().map(a -> new CreateAliasCommand(a.getAlias(), a.getLanguage())).toList()
                : List.of();
        return new CreatePlaceCommand(name, nameEn, nameTm, description, address, category, cityId, latitude, longitude, isActive, aliasCommands);
    }
}
