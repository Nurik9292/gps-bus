package biz.ugur.busroutebackend.shared.base;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import reactor.core.publisher.Mono;

public abstract class BaseUseCase<T, R> implements UseCase<T,R> {

    protected final CorrelationContextService correlationService;
    protected final EventBus eventBus;

    protected  BaseUseCase(CorrelationContextService correlationService, EventBus eventBus) {
        this.correlationService = correlationService;
        this.eventBus = eventBus;
    }


    @Override
    public Mono<R> execute(T request) {
        return correlationService.executeWithCorrelation(process(request), getBoundContext());
    }

    protected abstract Mono<R> process(T request);
    protected abstract String getBoundContext();

}
