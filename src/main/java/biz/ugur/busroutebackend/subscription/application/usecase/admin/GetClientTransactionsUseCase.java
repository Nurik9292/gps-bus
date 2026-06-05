package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.payment.application.dto.PaymentList;
import biz.ugur.busroutebackend.payment.application.dto.PaymentResponse;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentStatus;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.payment.domain.repository.PaymentRepository;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

@Service
public class GetClientTransactionsUseCase
        extends BaseUseCase<GetClientTransactionsUseCase.Query, PaymentList> {

    public record Query(String clientId, int page, int size, String status, Instant from, Instant to) {}

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRepository paymentRepository;

    public GetClientTransactionsUseCase(SubscriptionRepository subscriptionRepository,
                                        PaymentRepository paymentRepository,
                                        CorrelationContextService correlationService,
                                        EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRepository = paymentRepository;
    }

    @Override
    protected Mono<PaymentList> process(Query q) {
        PaymentStatus status = parseStatus(q.status());
        var pageable = PageRequest.of(q.page() - 1, q.size());

        return subscriptionRepository.findAllByClientId(q.clientId())
                .map(s -> s.getId().getValue())
                .collectList()
                .flatMap(subjectIds -> {
                    if (subjectIds.isEmpty()) {
                        return Mono.just(PaymentList.of(List.of(), 0L, q.page(), q.size(), 0));
                    }
                    var items = paymentRepository.findBySubjectTypeAndSubjectIdIn(
                                    PaymentSubjectType.CLIENT_SUBSCRIPTION, subjectIds, status, q.from(), q.to(), pageable)
                            .map(PaymentResponse::fromDomain)
                            .collectList();
                    var total = paymentRepository.countBySubjectTypeAndSubjectIdIn(
                            PaymentSubjectType.CLIENT_SUBSCRIPTION, subjectIds, status, q.from(), q.to());
                    return items.zipWith(total)
                            .map(tuple -> PaymentList.of(tuple.getT1(), null, q.page(), q.size(), tuple.getT2()));
                });
    }

    private PaymentStatus parseStatus(String raw) {
        return (raw == null || raw.isBlank()) ? null : PaymentStatus.valueOf(raw.trim().toUpperCase());
    }

    @Override
    protected String getBoundContext() { return "subscription.admin"; }
}
