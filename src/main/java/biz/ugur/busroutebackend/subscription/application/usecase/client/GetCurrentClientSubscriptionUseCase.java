package biz.ugur.busroutebackend.subscription.application.usecase.client;

import biz.ugur.busroutebackend.payment.application.usecase.admin.GetPaymentByIdUseCase;
import biz.ugur.busroutebackend.payment.application.usecase.admin.SyncPaymentStatusUseCase;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.PaymentOutcome;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionResponse;
import biz.ugur.busroutebackend.subscription.application.dto.TransactionSummary;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.infrastructure.config.SubscriptionProperties;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
public class GetCurrentClientSubscriptionUseCase
        extends BaseUseCase<String, SubscriptionResponse> {

    private final SubscriptionRepository subscriptionRepository;
    private final SyncPaymentStatusUseCase syncPaymentStatusUseCase;
    private final GetPaymentByIdUseCase getPaymentByIdUseCase;
    private final SubscriptionProperties properties;

    public GetCurrentClientSubscriptionUseCase(SubscriptionRepository subscriptionRepository,
                                               SyncPaymentStatusUseCase syncPaymentStatusUseCase,
                                               GetPaymentByIdUseCase getPaymentByIdUseCase,
                                               SubscriptionProperties properties,
                                               CorrelationContextService correlationService,
                                               EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
        this.syncPaymentStatusUseCase = syncPaymentStatusUseCase;
        this.getPaymentByIdUseCase = getPaymentByIdUseCase;
        this.properties = properties;
    }

    @Override
    protected String getBoundContext() {
        return "subscription.client";
    }

    @Override
    protected Mono<SubscriptionResponse> process(String clientId) {
        return subscriptionRepository.findActiveByClientId(clientId)
                .switchIfEmpty(Mono.defer(() -> subscriptionRepository.findLatestByClientId(clientId)))
                .flatMap(this::enrichWithTransaction);
    }

    private Mono<SubscriptionResponse> enrichWithTransaction(Subscription subscription) {
        if (subscription.getPaymentId() == null) {
            return Mono.just(SubscriptionResponse.fromDomain(subscription));
        }
        return resolveLatestTransaction(subscription.getPaymentId())
                .map(summary -> SubscriptionResponse.fromDomain(
                        subscription,
                        PaymentOutcome.fromPaymentStatus(summary.status()).name(),
                        summary))
                .defaultIfEmpty(SubscriptionResponse.fromDomain(subscription));
    }

    private Mono<TransactionSummary> resolveLatestTransaction(String paymentId) {
        return syncPaymentStatusUseCase.execute(paymentId)
                .timeout(Duration.ofMillis(properties.getPaymentStatusSyncTimeoutMs()))
                .onErrorResume(e -> getPaymentByIdUseCase.execute(paymentId))
                .map(TransactionSummary::fromPaymentResponse)
                .onErrorResume(e -> Mono.empty());
    }
}
