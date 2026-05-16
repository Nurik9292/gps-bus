package biz.ugur.busroutebackend.payment.domain.repository;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.model.Payment;
import biz.ugur.busroutebackend.payment.domain.valueobjects.PaymentId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

public interface PaymentRepository extends BaseRepository<Payment, PaymentId> {

    Mono<Payment> findByOrderNumber(String orderNumber);

    Mono<Payment> findByProviderOrderId(PaymentProvider provider, String providerOrderId);

    Flux<Payment> findByStatus(PaymentStatus status, Pageable pageable);

    Mono<Long> countByStatus(PaymentStatus status);

    Flux<Payment> findPendingStale(long staleAfterSeconds, Pageable pageable);

    Mono<Payment> findFirstBySubjectAndProviderAndStatus(
            PaymentSubjectType subjectType,
            String subjectId,
            PaymentProvider provider,
            PaymentStatus status
    );

    Mono<Long> sumCompletedRevenueBetween(Instant from, Instant to);

    Mono<Long> countByProviderAndStatus(PaymentProvider provider, PaymentStatus status);
}
