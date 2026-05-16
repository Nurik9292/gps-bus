package biz.ugur.busroutebackend.advertising.domain.repository;

import java.math.BigDecimal;

public record SalesReportRowTotals(
        long orders,
        long revenue,
        String currency,
        BigDecimal avgCtr
) {}
