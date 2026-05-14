package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.application.dto.AdPlacementAnalyticsResponse.Period;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

public record AdPlacementAnalyticsTrendResponse(
        @JsonProperty("placement_id") String placementId,
        @JsonProperty("period")       Period period,
        @JsonProperty("points")       List<DailyAnalyticsPoint> points
) {
    public static AdPlacementAnalyticsTrendResponse of(
            PlacementId placementId, Instant from, Instant to,
            List<DailyAnalyticsPoint> points) {
        return new AdPlacementAnalyticsTrendResponse(
                placementId.getValue(),
                new Period(from, to),
                points
        );
    }
}
