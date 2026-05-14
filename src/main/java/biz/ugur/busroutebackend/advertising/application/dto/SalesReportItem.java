package biz.ugur.busroutebackend.advertising.application.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalesReportItem(
        String placementId,
        String title,
        String placementStatus,
        String kind,
        String businessId,
        String businessName,
        String tariffId,
        String tariffName,
        Period period,
        Payment payment,
        Metrics metrics
) {
    public record Period(
            LocalDateTime startsAt,
            LocalDateTime endsAt
    ) {}

    public record Payment(
            String paymentId,
            String provider,
            String status,
            Long amountMinor,
            String currency
    ) {}

    public record Metrics(
            long impressions,
            long clicks,
            BigDecimal ctr
    ) {}
}
