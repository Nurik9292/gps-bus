package biz.ugur.busroutebackend.subscription.application.usecase.admin;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.base.BaseUseCase;
import biz.ugur.busroutebackend.subscription.application.dto.SubscriptionPriceResponse;
import biz.ugur.busroutebackend.subscription.application.dto.UpdateSubscriptionPriceCommand;
import biz.ugur.busroutebackend.subscription.domain.enums.SubscriptionPeriod;
import biz.ugur.busroutebackend.subscription.domain.exceptions.SubscriptionNotFoundException;
import biz.ugur.busroutebackend.subscription.domain.model.SubscriptionPlanPrice;
import biz.ugur.busroutebackend.subscription.domain.repository.SubscriptionPlanPriceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class UpdateSubscriptionPriceUseCase
        extends BaseUseCase<UpdateSubscriptionPriceCommand, SubscriptionPriceResponse> {

    private final SubscriptionPlanPriceRepository priceRepository;

    public UpdateSubscriptionPriceUseCase(SubscriptionPlanPriceRepository priceRepository,
                                          CorrelationContextService correlationService,
                                          EventBus eventBus) {
        super(correlationService, eventBus);
        this.priceRepository = priceRepository;
    }

    @Override
    protected Mono<SubscriptionPriceResponse> process(UpdateSubscriptionPriceCommand cmd) {
        SubscriptionPeriod period = SubscriptionPeriod.from(cmd.period());
        return priceRepository.findByPeriod(period)
                .switchIfEmpty(Mono.error(new SubscriptionNotFoundException(
                        "subscription price for period " + period.name() + " not found")))
                .map(price -> price.changeAmount(cmd.amountMinor(), cmd.updatedBy()))
                .flatMap(this::saveAndPublish)
                .map(SubscriptionPriceResponse::fromDomain)
                .doOnSuccess(r -> log.info("Subscription price updated: period={} amountMinor={} by={}",
                        r.period(), r.amountMinor(), cmd.updatedBy()));
    }

    private Mono<SubscriptionPlanPrice> saveAndPublish(SubscriptionPlanPrice price) {
        return priceRepository.save(price)
                .doOnSuccess(saved -> {
                    price.getDomainEvents().forEach(eventBus::publish);
                    price.clearEvents();
                });
    }

    @Override
    protected String getBoundContext() { return "subscription.admin"; }
}
