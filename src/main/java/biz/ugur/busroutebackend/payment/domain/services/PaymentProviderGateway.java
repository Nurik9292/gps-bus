package biz.ugur.busroutebackend.payment.domain.services;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import reactor.core.publisher.Mono;


public interface PaymentProviderGateway {

    boolean supports(PaymentProvider provider);

    Mono<RegisterResult> register(Payment payment);

    Mono<StatusResult> getStatus(Payment payment);

    Mono<ActionResult> reverse(Payment payment);

    Mono<ActionResult> refund(Payment payment, long amountMinor);

    record RegisterResult(String providerOrderId, String formUrl) {}

    record StatusResult(
            int orderStatusCode,
            String errorCode,
            String errorMessage,
            long amountMinor,
            String cardPanMasked,
            String cardExpiration,
            String cardholderName
    ) {}

    record ActionResult(boolean success, String errorCode, String errorMessage) {}
}
