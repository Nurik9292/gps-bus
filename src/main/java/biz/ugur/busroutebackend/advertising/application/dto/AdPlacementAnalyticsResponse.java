package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.repository.DetailViewSummary;
import biz.ugur.busroutebackend.advertising.domain.valueobjects.PlacementId;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record AdPlacementAnalyticsResponse(
        @JsonProperty("placement_id") String placementId,
        @JsonProperty("period")       Period period,
        @JsonProperty("impressions")  long impressions,
        @JsonProperty("clicks")       long clicks,
        @JsonProperty("ctr")          BigDecimal ctr,
        @JsonProperty("detail_views") long detailViews,
        @JsonProperty("avg_dwell_ms") Long avgDwellMs
) {
    public record Period(
            @JsonProperty("from") Instant from,
            @JsonProperty("to")   Instant to
    ) {}

    public static AdPlacementAnalyticsResponse of(
            PlacementId placementId, Instant from, Instant to,
            long impressions, long clicks, DetailViewSummary detailSummary) {
        BigDecimal ctr = impressions == 0 ? null
                : BigDecimal.valueOf(clicks).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP);
        Long avgDwell = detailSummary.count() == 0 ? null : detailSummary.avgDurationMs();
        return new AdPlacementAnalyticsResponse(
                placementId.getValue(),
                new Period(from, to),
                impressions,
                clicks,
                ctr,
                detailSummary.count(),
                avgDwell
        );
    }
}
