package biz.ugur.busroutebackend.payment.domain.services;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.exceptions.PaymentProviderException;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Selects the {@link PaymentProviderGateway} for a given {@link PaymentProvider}.
 * Injected into use cases that need to interact with an external gateway.
 */
public class PaymentOrchestrator {

    private final List<PaymentProviderGateway> gateways;

    public PaymentOrchestrator(List<PaymentProviderGateway> gateways) {
        this.gateways = List.copyOf(gateways);
    }

    public PaymentProviderGateway resolve(PaymentProvider provider) {
        return gateways.stream()
                .filter(g -> g.supports(provider))
                .findFirst()
                .orElseThrow(() -> new PaymentProviderException(null,
                        "no enabled payment gateway supports provider: " + provider));
    }

    public Mono<PaymentProviderGateway.RegisterResult> register(Payment payment) {
        return resolve(payment.getProvider()).register(payment);
    }

    public Mono<PaymentProviderGateway.StatusResult> getStatus(Payment payment) {
        return resolve(payment.getProvider()).getStatus(payment);
    }

    public Mono<PaymentProviderGateway.ActionResult> reverse(Payment payment) {
        return resolve(payment.getProvider()).reverse(payment);
    }

    public Mono<PaymentProviderGateway.ActionResult> refund(Payment payment, long amountMinor) {
        return resolve(payment.getProvider()).refund(payment, amountMinor);
    }
}
