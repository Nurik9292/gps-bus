package biz.ugur.busroutebackend.shared.base;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.ReactiveUseCase;
import biz.ugur.busroutebackend.shared.application.UseCase;
import biz.ugur.busroutebackend.shared.domain.entity.AggregateRoot;
import reactor.core.publisher.Mono;

public abstract class BaseUseCase<T, R> implements UseCase<T, Mono<R>>, ReactiveUseCase<T, R> {

    protected final CorrelationContextService correlationService;
    protected final EventBus eventBus;

    protected  BaseUseCase(CorrelationContextService correlationService, EventBus eventBus) {
        this.correlationService = correlationService;
        this.eventBus = eventBus;
    }


    @Override
    public Mono<R> execute(T request) {
        return correlationService.executeWithCorrelation(Mono.defer(() -> process(request)), getBoundContext());
    }

    protected <T extends AggregateRoot<?, ?>> Mono<T> persistAndPublish(Mono<T> aggregateMono) {
        return aggregateMono.flatMap(aggregate -> {
            aggregate.getDomainEvents().forEach(eventBus::publish);
            aggregate.clearEvents();
            return Mono.just(aggregate);
        });
    }

    protected abstract Mono<R> process(T request);
    protected abstract String getBoundContext();

}
