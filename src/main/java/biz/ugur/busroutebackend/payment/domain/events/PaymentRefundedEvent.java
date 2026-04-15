package biz.ugur.busroutebackend.payment.domain.events;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import lombok.Getter;

@Getter
public class PaymentRefundedEvent extends PaymentDomainEvent {

    private final PaymentSubjectType subjectType;
    private final String subjectId;
    private final long refundedAmountMinor;
    private final String currency;

    public PaymentRefundedEvent(String paymentId,
                                 PaymentSubjectType subjectType,
                                 String subjectId,
                                 long refundedAmountMinor,
                                 String currency) {
        super(paymentId);
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.refundedAmountMinor = refundedAmountMinor;
        this.currency = currency;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
