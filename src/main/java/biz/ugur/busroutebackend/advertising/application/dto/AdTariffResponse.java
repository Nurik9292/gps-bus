package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.model.AdTariff;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record AdTariffResponse(
        @JsonProperty("id")                    String id,
        @JsonProperty("name")                  String name,
        @JsonProperty("description")           String description,
        @JsonProperty("placement_type")        String placementType,
        @JsonProperty("period")                String period,
        @JsonProperty("price_amount")          long priceAmount,
        @JsonProperty("price_major")           double priceMajor,
        @JsonProperty("currency")              String currency,
        @JsonProperty("max_impressions")       Integer maxImpressions,
        @JsonProperty("max_clicks")            Integer maxClicks,
        @JsonProperty("daily_impression_cap")  Integer dailyImpressionCap,
        @JsonProperty("is_active")             Boolean isActive,
        @JsonProperty("display_order")         Integer displayOrder,

        @JsonProperty("created_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
        LocalDateTime updatedAt
) {

    public static AdTariffResponse fromDomain(AdTariff t) {
        return new AdTariffResponse(
                t.getId().getValue(),
                t.getName().getValue(),
                t.getDescription(),
                t.getPlacementType().name(),
                t.getPeriod().name(),
                t.getPrice().getAmountMinor(),
                t.getPrice().toMajorUnits(),
                t.getPrice().getCurrency(),
                t.getMaxImpressions(),
                t.getMaxClicks(),
                t.getDailyImpressionCap(),
                t.getIsActive(),
                t.getDisplayOrder(),
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
