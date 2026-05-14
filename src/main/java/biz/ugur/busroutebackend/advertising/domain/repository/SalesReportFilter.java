package biz.ugur.busroutebackend.advertising.domain.repository;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;

import java.time.Instant;

public record SalesReportFilter(
        Instant from,
        Instant to,
        int page,
        int size,
        PaymentStatus paymentStatus,
        PaymentProvider provider
) {
    public int offset() {
        return (page - 1) * size;
    }
}
