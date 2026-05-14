package biz.ugur.busroutebackend.advertising.application.dto;

import java.math.BigDecimal;

public record SalesReportTotals(
        long orders,
        long revenue,
        String currency,
        BigDecimal avgCtr
) {}
