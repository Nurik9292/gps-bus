package biz.ugur.busroutebackend.transport.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

public record RouteAlternativeDTO(
        @JsonProperty("id")
        String id,

        @JsonProperty("primary_route_id")
        String primaryRouteId,

        @JsonProperty("primary_route_number")
        String primaryRouteNumber,

        @JsonProperty("primary_route_name")
        String primaryRouteName,

        @JsonProperty("alternative_route_id")
        String alternativeRouteId,

        @JsonProperty("alternative_route_number")
        String alternativeRouteNumber,

        @JsonProperty("alternative_route_name")
        String alternativeRouteName,

        @JsonProperty("alternative_route_is_active")
        Boolean alternativeRouteIsActive,

        @JsonProperty("priority")
        Integer priority,

        @JsonProperty("description")
        String description,

        @JsonProperty("description_tm")
        String descriptionTm,

        @JsonProperty("description_en")
        String descriptionEn,

        @JsonProperty("created_at")
        LocalDateTime createdAt,

        @JsonProperty("updated_at")
        LocalDateTime updatedAt
) {
    public static RouteAlternativeDTO simple(
            String id,
            String primaryRouteId,
            String alternativeRouteId,
            Integer priority,
            String description,
            String descriptionTm,
            String descriptionEn,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        return new RouteAlternativeDTO(
                id, primaryRouteId, null, null,
                alternativeRouteId, null, null, null,
                priority, description, descriptionTm, descriptionEn,
                createdAt, updatedAt
        );
    }
}
