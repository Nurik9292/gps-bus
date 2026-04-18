package biz.ugur.busroutebackend.payment.domain.events;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import lombok.Getter;


@Getter
public class PaymentCompletedEvent extends PaymentDomainEvent {

    private final PaymentSubjectType subjectType;
    private final String subjectId;
    private final long amountMinor;
    private final String currency;

    public PaymentCompletedEvent(String paymentId,
                                  PaymentSubjectType subjectType,
                                  String subjectId,
                                  long amountMinor,
                                  String currency) {
        super(paymentId);
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
