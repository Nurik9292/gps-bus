package biz.ugur.busroutebackend.payment.domain.events;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import lombok.Getter;

@Getter
public class PaymentInitiatedEvent extends PaymentDomainEvent {

    private final PaymentProvider provider;
    private final PaymentSubjectType subjectType;
    private final String subjectId;
    private final long amountMinor;
    private final String currency;

    public PaymentInitiatedEvent(String paymentId,
                                  PaymentProvider provider,
                                  PaymentSubjectType subjectType,
                                  String subjectId,
                                  long amountMinor,
                                  String currency) {
        super(paymentId);
        this.provider = provider;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.amountMinor = amountMinor;
        this.currency = currency;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
