package biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.List;

public record RoutingAnalyticsResponse(
        @JsonProperty("overview") OverviewResponse overview,
        @JsonProperty("daily_searches") List<DailySearchResponse> dailySearches,
        @JsonProperty("hourly_searches") List<HourlySearchResponse> hourlySearches,
        @JsonProperty("transfer_distribution") List<TransferDistributionResponse> transferDistribution,
        @JsonProperty("route_popularity") List<RoutePopularityResponse> routePopularity
) {

    public record OverviewResponse(
            @JsonProperty("total_searches") long totalSearches,
            @JsonProperty("successful_searches") long successfulSearches,
            @JsonProperty("failed_searches") long failedSearches,
            @JsonProperty("avg_options_per_search") double avgOptionsPerSearch,
            @JsonProperty("avg_duration_minutes") double avgDurationMinutes,
            @JsonProperty("success_rate") double successRate
    ) {}

    public record DailySearchResponse(
            @JsonProperty("date") LocalDateTime date,
            @JsonProperty("total_searches") int totalSearches,
            @JsonProperty("successful_searches") int successfulSearches,
            @JsonProperty("failed_searches") int failedSearches
    ) {}

    public record HourlySearchResponse(
            @JsonProperty("hour") LocalDateTime hour,
            @JsonProperty("total_searches") int totalSearches,
            @JsonProperty("successful_searches") int successfulSearches,
            @JsonProperty("failed_searches") int failedSearches,
            @JsonProperty("avg_options") double avgOptions
    ) {}

    public record TransferDistributionResponse(
            @JsonProperty("number_of_transfers") int numberOfTransfers,
            @JsonProperty("count") long count
    ) {}

    public record RoutePopularityResponse(
            @JsonProperty("route_id") String routeId,
            @JsonProperty("route_number") String routeNumber,
            @JsonProperty("route_name") String routeName,
            @JsonProperty("usage_count") long usageCount
    ) {}
}
