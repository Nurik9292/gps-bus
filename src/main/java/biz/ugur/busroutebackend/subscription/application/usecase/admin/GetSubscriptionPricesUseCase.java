package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionPriceResponse;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionPlanPriceRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
public class GetSubscriptionPricesUseCase extends BaseUseCase<Void, List<SubscriptionPriceResponse>> {

    private final SubscriptionPlanPriceRepository priceRepository;

    public GetSubscriptionPricesUseCase(SubscriptionPlanPriceRepository priceRepository,
                                        CorrelationContextService correlationService,
                                        EventBus eventBus) {
        super(correlationService, eventBus);
        this.priceRepository = priceRepository;
    }

    @Override
    protected Mono<List<SubscriptionPriceResponse>> process(Void unused) {
        return priceRepository.findAllPrices()
                .map(SubscriptionPriceResponse::fromDomain)
                .collectList();
    }

    @Override
    protected String getBoundContext() { return "subscription.admin"; }
}
