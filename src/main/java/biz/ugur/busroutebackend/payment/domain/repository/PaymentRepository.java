package biz.ugur.busroutebackend.payment.domain.repository;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface PaymentRepository extends BaseRepository<Payment, PaymentId> {

    Mono<Payment> findByOrderNumber(String orderNumber);

    Mono<Payment> findByProviderOrderId(PaymentProvider provider, String providerOrderId);

    Flux<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Mono<Long> countByStatus(PaymentStatus status);

    /**
     * Payments that are still open (REGISTERED or PREAUTH) and were initiated more than
     * {@code staleAfterSeconds} ago. Used by the status-sync scheduler.
     */
    Flux<Payment> findPendingStale(long staleAfterSeconds, Pageable pageable);
}
