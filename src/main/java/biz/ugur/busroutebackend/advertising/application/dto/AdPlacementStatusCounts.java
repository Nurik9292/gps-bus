package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.enums.PlacementStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public record AdPlacementStatusCounts(
        @JsonProperty("draft")           long draft,
        @JsonProperty("pending_payment") long pendingPayment,
        @JsonProperty("scheduled")       long scheduled,
        @JsonProperty("active")          long active,
        @JsonProperty("expired")         long expired,
        @JsonProperty("cancelled")       long cancelled,
        @JsonProperty("total")           long total
) {
    public static AdPlacementStatusCounts from(Map<PlacementStatus, Long> counts) {
        long draft     = counts.getOrDefault(PlacementStatus.DRAFT, 0L);
        long pending   = counts.getOrDefault(PlacementStatus.PENDING_PAYMENT, 0L);
        long scheduled = counts.getOrDefault(PlacementStatus.SCHEDULED, 0L);
        long active    = counts.getOrDefault(PlacementStatus.ACTIVE, 0L);
        long expired   = counts.getOrDefault(PlacementStatus.EXPIRED, 0L);
        long cancelled = counts.getOrDefault(PlacementStatus.CANCELLED, 0L);
        long total = draft + pending + scheduled + active + expired + cancelled;
        return new AdPlacementStatusCounts(draft, pending, scheduled, active, expired, cancelled, total);
    }
}
