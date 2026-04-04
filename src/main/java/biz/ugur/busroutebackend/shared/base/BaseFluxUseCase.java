package biz.ugur.busroutebackend.shared.base;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.UseCase;
import reactor.core.publisher.Flux;

public abstract class BaseFluxUseCase<T, R> implements UseCase<T, Flux<R>> {

    protected final CorrelationContextService correlationService;
    protected final EventBus eventBus;

    protected BaseFluxUseCase(
            CorrelationContextService correlationService,
            EventBus eventBus
    ) {
        this.correlationService = correlationService;
        this.eventBus = eventBus;
    }

    @Override
    public Flux<R> execute(T request) {
        return correlationService.getCurrentCorrelationId()
                .flatMapMany(correlationId -> {
                    logStart(request, correlationId);
                    return process(request)
                            .doOnComplete(() -> logComplete(request, correlationId))
                            .doOnError(error -> logError(request, correlationId, error));
                });
    }

    protected abstract Flux<R> process(T request);

    protected abstract String getBoundedContext();

    protected void logStart(T request, Object correlationId) {
    }

    protected void logComplete(T request, Object correlationId) {
    }

    protected void logError(T request, Object correlationId, Throwable error) {
    }
}