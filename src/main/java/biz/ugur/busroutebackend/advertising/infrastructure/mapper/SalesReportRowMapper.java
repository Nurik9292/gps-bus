package biz.ugur.busroutebackend.advertising.infrastructure.mapper;

import biz.ugur.busroutebackend.advertising.domain.repository.SalesReportRow;
import io.r2dbc.spi.Readable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

public final class SalesReportRowMapper {

    private SalesReportRowMapper() {}

    public static SalesReportRow mapRow(Readable row) {
        long impressions = row.get("impressions", Long.class);
        long clicks      = row.get("clicks", Long.class);
        BigDecimal ctr = impressions == 0 ? null
                : BigDecimal.valueOf(clicks)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(impressions), 2, RoundingMode.HALF_UP);

        String paymentId = row.get("payment_id", String.class);
        SalesReportRow.Payment payment = paymentId == null ? null : new SalesReportRow.Payment(
                paymentId,
                row.get("provider", String.class),
                row.get("payment_status", String.class),
                row.get("amount_minor", Long.class),
                row.get("currency", String.class)
        );

        return new SalesReportRow(
                row.get("id", String.class),
                row.get("title", String.class),
                row.get("placement_status", String.class),
                row.get("kind", String.class),
                row.get("business_id", String.class),
                row.get("business_name", String.class),
                row.get("tariff_id", String.class),
                row.get("tariff_name", String.class),
                new SalesReportRow.Period(
                        row.get("starts_at", LocalDateTime.class),
                        row.get("ends_at", LocalDateTime.class)
                ),
                payment,
                new SalesReportRow.Metrics(impressions, clicks, ctr)
        );
    }
}
