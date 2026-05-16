package biz.ugur.busroutebackend.advertising.application.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyAnalyticsPoint(
        @JsonProperty("day")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate day,
        @JsonProperty("impressions")  long impressions,
        @JsonProperty("clicks")       long clicks,
        @JsonProperty("ctr")          BigDecimal ctr,
        @JsonProperty("detail_views") long detailViews,
        @JsonProperty("avg_dwell_ms") Long avgDwellMs
) {}
