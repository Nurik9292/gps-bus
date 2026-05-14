package biz.ugur.busroutebackend.advertising.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record AdvertisingDashboardOverview(
        @JsonProperty("window_days")         int windowDays,
        @JsonProperty("active_placements")   long activePlacements,
        @JsonProperty("revenue_30d")         long revenue30d,
        @JsonProperty("currency")            String currency,
        @JsonProperty("pending_cash_count")  long pendingCashCount,
        @JsonProperty("impressions_30d")     long impressions30d,
        @JsonProperty("clicks_30d")          long clicks30d,
        @JsonProperty("ctr_30d")             BigDecimal ctr30d
) {
    public static AdvertisingDashboardOverview of(
            int windowDays, long active, long revenue, String currency,
            long pendingCash, long impressions, long clicks) {
        BigDecimal ctr = impressions == 0 ? null
                : BigDecimal.valueOf(clicks).multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP);
        return new AdvertisingDashboardOverview(
                windowDays, active, revenue, currency, pendingCash, impressions, clicks, ctr);
    }
}
