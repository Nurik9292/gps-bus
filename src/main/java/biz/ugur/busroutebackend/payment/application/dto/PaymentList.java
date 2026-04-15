package biz.ugur.busroutebackend.payment.application.dto;

import biz.ugur.busroutebackend.shared.application.dto.PagedList;
import biz.ugur.busroutebackend.shared.application.dto.PaginationInfo;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.Collections;
import java.util.List;

@ToString
@Getter
@EqualsAndHashCode
public final class PaymentList implements PagedList<PaymentResponse> {

    private final List<PaymentResponse> payments;
    private final Long activeCount;
    private final PaginationInfo pagination;

    public PaymentList(List<PaymentResponse> payments, Long activeCount,
                        int currentPage, int pageSize, long totalItems) {
        this.payments = Collections.unmodifiableList(payments);
        this.activeCount = activeCount;
        this.pagination = PaginationInfo.of(currentPage, pageSize, totalItems);
    }

    public static PaymentList of(List<PaymentResponse> payments, Long activeCount,
                                   int currentPage, int pageSize, long totalItems) {
        return new PaymentList(payments, activeCount, currentPage, pageSize, totalItems);
    }

    @Override public List<PaymentResponse> items() { return payments; }
    @Override public Long activeCount() { return activeCount; }
    @Override public PaginationInfo pagination() { return pagination; }
}
