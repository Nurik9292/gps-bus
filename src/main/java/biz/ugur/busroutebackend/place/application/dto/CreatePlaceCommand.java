package biz.ugur.busroutebackend.place.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record CreatePlaceCommand(
        String name,
        String nameEn,
        String nameTm,
        String description,
        String address,
        String category,
        String cityId,
        BigDecimal latitude,
        BigDecimal longitude,
        Boolean isActive,
        List<CreateAliasCommand> aliases
) {}
