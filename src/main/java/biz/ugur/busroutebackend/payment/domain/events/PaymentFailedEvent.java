package biz.ugur.busroutebackend.payment.domain.events;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import lombok.Getter;

@Getter
public class PaymentFailedEvent extends PaymentDomainEvent {

    private final PaymentStatus terminalStatus;
    private final PaymentSubjectType subjectType;
    private final String subjectId;
    private final String failureCode;
    private final String failureMessage;

    public PaymentFailedEvent(String paymentId,
                               PaymentStatus terminalStatus,
                               PaymentSubjectType subjectType,
                               String subjectId,
                               String failureCode,
                               String failureMessage) {
        super(paymentId);
        this.terminalStatus = terminalStatus;
        this.subjectType = subjectType;
        this.subjectId = subjectId;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    @Override protected int getCurrentVersion() { return 1; }
}
