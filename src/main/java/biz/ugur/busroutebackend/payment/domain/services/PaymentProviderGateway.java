package biz.ugur.busroutebackend.payment.domain.services;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import reactor.core.publisher.Mono;

/**
 * Domain-facing port for external payment providers.
 *
 * <p>Implementations live in {@code payment/infrastructure/provider/}. Each concrete
 * implementation owns the HTTP protocol of one provider family (e.g. sv_epg for Rysgal+Senagat).
 * The orchestrator {@code PaymentOrchestrator} routes requests to the matching gateway based
 * on {@link PaymentProvider}.
 */
public interface PaymentProviderGateway {

    /** Which provider(s) this gateway handles. */
    boolean supports(PaymentProvider provider);

    /**
     * Registers the order on the provider side. On success, the returned result contains
     * {@code providerOrderId} (for subsequent status/refund calls) and {@code formUrl}
     * (where to redirect the customer to pay).
     */
    Mono<RegisterResult> register(Payment payment);

    /** Polls the provider for current order status. */
    Mono<StatusResult> getStatus(Payment payment);

    /** Cancels an authorized (but not yet captured) payment. */
    Mono<ActionResult> reverse(Payment payment);

    /** Refunds a completed payment back to the customer. Amount in minor units. */
    Mono<ActionResult> refund(Payment payment, long amountMinor);

    record RegisterResult(String providerOrderId, String formUrl) {}

    /**
     * @param orderStatusCode native code from the provider (for sv_epg: 0..6)
     * @param errorCode       provider error code (may be null on success)
     * @param errorMessage    human-readable error description (may be null on success)
     * @param amountMinor     amount as seen by the provider (0 if not populated)
     * @param cardPanMasked   masked PAN on successful completion (nullable)
     * @param cardExpiration  card expiration in YYYYMM format (nullable)
     * @param cardholderName  name on the card (nullable)
     */
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
