package biz.ugur.busroutebackend.advertising.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UpdateAdTariffCommand(
        @JsonProperty("name")                  String name,
        @JsonProperty("description")           String description,
        @JsonProperty("price_amount")          Long priceAmount,
        @JsonProperty("currency")              String currency,
        @JsonProperty("max_impressions")       Integer maxImpressions,
        @JsonProperty("max_clicks")            Integer maxClicks,
        @JsonProperty("daily_impression_cap")  Integer dailyImpressionCap,
        @JsonProperty("display_order")         Integer displayOrder
) {}
