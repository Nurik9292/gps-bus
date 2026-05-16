package biz.ugur.busroutebackend.advertising.application.dto;

import biz.ugur.busroutebackend.advertising.domain.repository.SalesReportRowTotals;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record SalesReportTotals(
        @JsonProperty("orders")   long orders,
        @JsonProperty("revenue")  long revenue,
        @JsonProperty("currency") String currency,
        @JsonProperty("avg_ctr")  BigDecimal avgCtr
) {
    public static SalesReportTotals fromDomain(SalesReportRowTotals r) {
        return new SalesReportTotals(r.orders(), r.revenue(), r.currency(), r.avgCtr());
    }
}
