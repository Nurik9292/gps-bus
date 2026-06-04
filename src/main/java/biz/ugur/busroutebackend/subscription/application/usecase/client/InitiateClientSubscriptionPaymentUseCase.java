package biz.ugur.busroutebackend.subscription.application.usecase.client;

import biz.ugur.busroutebackend.payment.application.dto.InitiatePaymentCommand;
import biz.ugur.busroutebackend.payment.application.usecase.admin.InitiatePaymentUseCase;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentProvider;
import biz.ugur.busroutebackend.payment.domain.enums.PaymentSubjectType;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.InitiateSubscriptionPaymentCommand;
import biz.ugur.busroutebackend.subscription.application.dto.InitiateSubscriptionPaymentResponse;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.model.Subscription;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionRepository;
import biz.ugur.busroutebackend.subscription.infrastructure.config.SubscriptionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
public class InitiateClientSubscriptionPaymentUseCase
        extends BaseUseCase<InitiateSubscriptionPaymentCommand, InitiateSubscriptionPaymentResponse> {

    private final SubscriptionRepository subscriptionRepository;
    private final InitiatePaymentUseCase initiatePaymentUseCase;
    private final SubscriptionProperties properties;

    public InitiateClientSubscriptionPaymentUseCase(SubscriptionRepository subscriptionRepository,
                                                     InitiatePaymentUseCase initiatePaymentUseCase,
                                                     SubscriptionProperties properties,
                                                     CorrelationContextService correlationService,
                                                     EventBus eventBus) {
        super(correlationService, eventBus);
        this.subscriptionRepository = subscriptionRepository;
        this.initiatePaymentUseCase = initiatePaymentUseCase;
        this.properties = properties;
    }

    @Override
    protected String getBoundContext() {
        return "subscription.client";
    }

    @Override
    protected Mono<InitiateSubscriptionPaymentResponse> process(InitiateSubscriptionPaymentCommand cmd) {
        PaymentProvider provider = PaymentProvider.from(cmd.bank());
        SubscriptionPeriod period = SubscriptionPeriod.from(cmd.period());
        long amountMinor = amountFor(period);
        String currency = properties.getCurrency();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(properties.getPaymentSessionMinutes());

        Subscription draft = Subscription.initiate(cmd.clientId(), period, amountMinor, currency);

        return subscriptionRepository.save(draft)
                .transform(this::persistAndPublish)
                .flatMap(savedSubscription -> {
                    InitiatePaymentCommand paymentCmd = new InitiatePaymentCommand(
                            provider.name(),
                            PaymentSubjectType.CLIENT_SUBSCRIPTION.name(),
                            savedSubscription.getId().getValue(),
                            null,
                            amountMinor,
                            currency,
                            properties.getPaymentReturnUrl(),
                            properties.getPaymentSessionMinutes()
                    );
                    return initiatePaymentUseCase.execute(Mono.just(paymentCmd))
                            .flatMap(paymentResponse -> subscriptionRepository
                                    .save(savedSubscription.attachPayment(paymentResponse.paymentId()))
                                    .map(persistedSubscription -> new InitiateSubscriptionPaymentResponse(
                                            persistedSubscription.getId().getValue(),
                                            paymentResponse.paymentId(),
                                            paymentResponse.orderNumber(),
                                            paymentResponse.formUrl(),
                                            properties.getPaymentReturnUrl(),
                                            paymentResponse.provider(),
                                            period.name(),
                                            amountMinor,
                                            currency,
                                            expiresAt
                                    )));
                })
                .doOnSuccess(r -> log.info("Subscription payment initiated: subscriptionId={} paymentId={} provider={} period={}",
                        r.subscriptionId(), r.paymentId(), r.provider(), r.period()))
                .doOnError(e -> log.warn("Subscription payment initiation failed: {}", e.getMessage()));
    }

    private long amountFor(SubscriptionPeriod period) {
        return switch (period) {
            case MONTHLY -> properties.getMonthlyAmountMinor();
            case YEARLY  -> properties.getYearlyAmountMinor();
        };
    }
}
