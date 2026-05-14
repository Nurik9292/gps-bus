package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.repository.SalesReportRow;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SalesReportItem(
        @JsonProperty("placement_id")     String placementId,
        @JsonProperty("placement_title")  String placementTitle,
        @JsonProperty("placement_status") String placementStatus,
        @JsonProperty("kind")             String kind,
        @JsonProperty("business_id")      String businessId,
        @JsonProperty("business_name")    String businessName,
        @JsonProperty("tariff_id")        String tariffId,
        @JsonProperty("tariff_name")      String tariffName,
        @JsonProperty("period")           Period period,
        @JsonProperty("payment")          Payment payment,
        @JsonProperty("metrics")          Metrics metrics
) {
    public record Period(
            @JsonProperty("starts_at") LocalDateTime startsAt,
            @JsonProperty("ends_at")   LocalDateTime endsAt
    ) {}

    public record Payment(
            @JsonProperty("payment_id")    String paymentId,
            @JsonProperty("provider")      String provider,
            @JsonProperty("status")        String status,
            @JsonProperty("amount_minor")  Long amountMinor,
            @JsonProperty("currency")      String currency
    ) {}

    public record Metrics(
            @JsonProperty("impressions") long impressions,
            @JsonProperty("clicks")      long clicks,
            @JsonProperty("ctr")         BigDecimal ctr
    ) {}

    public static SalesReportItem fromDomain(SalesReportRow r) {
        Payment payment = r.payment() == null ? null : new Payment(
                r.payment().paymentId(),
                r.payment().provider(),
                r.payment().status(),
                r.payment().amountMinor(),
                r.payment().currency());
        return new SalesReportItem(
                r.placementId(),
                r.title(),
                r.placementStatus(),
                r.kind(),
                r.businessId(),
                r.businessName(),
                r.tariffId(),
                r.tariffName(),
                new Period(r.period().startsAt(), r.period().endsAt()),
                payment,
                new Metrics(r.metrics().impressions(), r.metrics().clicks(), r.metrics().ctr()));
    }
}
