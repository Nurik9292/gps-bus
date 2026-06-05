package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.PaymentAttemptsSummary;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

@Service
public class GetClientPaymentAttemptsUseCase extends BaseUseCase<String, PaymentAttemptsSummary> {

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    public GetClientPaymentAttemptsUseCase(SubscriptionRepository subscriptionRepository,
                                           PaymentRepository paymentRepository,
                                           CorrelationContextService correlationService,
                                           EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    protected Mono<PaymentAttemptsSummary> process(String clientId) {
        return subscriptionRepository.findAllByClientId(clientId)
                .map(Subscription::getId)
                .map(id -> id.getValue())
                .collectList()
                .flatMap(subjectIds -> {
                    if (subjectIds.isEmpty()) {
                        return Mono.just(PaymentAttemptsSummary.fromStatusCounts(Map.of()));
                    }
                    return paymentRepository
                            .countBySubjectTypeAndSubjectIdInGroupByStatus(
                                    PaymentSubjectType.CLIENT_SUBSCRIPTION, subjectIds)
                            .map(PaymentAttemptsSummary::fromStatusCounts);
                });
    }

    @Override
    protected String getBoundContext() { return "subscription.admin"; }
}
