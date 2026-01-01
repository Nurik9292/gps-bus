package biz.ugur.busroutebackend.shared.infrastructure.messaging;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import biz.ugur.busroutebackend.shared.domain.event.DomainEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;


@Component
@Slf4j
public class ReactiveEventBus implements DomainEventPublisher {

    private static final int BUFFER_SIZE = 10_000;
    private static final Duration BUSY_LOOP_TIMEOUT = Duration.ofSeconds(1);

    private final Sinks.Many<DomainEvent> eventSink;
    private final Flux<DomainEvent> eventFlux;

    public ReactiveEventBus() {
        this.eventSink = Sinks.many().multicast().onBackpressureBuffer(BUFFER_SIZE);

        this.eventFlux = eventSink.asFlux()
                .publishOn(Schedulers.boundedElastic())
                .share();
    }

    @PostConstruct
    public void init() {
        log.info("ReactiveEventBus initialized with buffer size: {}", BUFFER_SIZE);
    }

    @PreDestroy
    public void destroy() {
        eventSink.tryEmitComplete();
        log.info("ReactiveEventBus shutdown");
    }


    @Override
    public void publish(DomainEvent event) {
        eventSink.emitNext(event, (signalType, emitResult) -> {
            if (emitResult == Sinks.EmitResult.FAIL_NON_SERIALIZED) {
                return true;
            }
            handleEmitFailure(event, emitResult);
            return false;
        });

        log.trace("Event published: {}", event.getClass().getSimpleName());
    }

    private void handleEmitFailure(DomainEvent event, Sinks.EmitResult result) {
        switch (result) {
            case FAIL_OVERFLOW ->
                log.warn("Event buffer overflow, dropping event: {}", event.getClass().getSimpleName());
            case FAIL_CANCELLED ->
                log.debug("Event sink cancelled, dropping event: {}", event.getClass().getSimpleName());
            case FAIL_TERMINATED ->
                log.warn("Event sink terminated, dropping event: {}", event.getClass().getSimpleName());
            case FAIL_NON_SERIALIZED ->
                log.warn("Concurrent emit conflict, dropping event: {}", event.getClass().getSimpleName());
            default ->
                log.error("Failed to emit event {}: {}", event.getClass().getSimpleName(), result);
        }
    }


    public <T extends DomainEvent> Flux<T> on(Class<T> eventType) {
        return eventFlux
                .filter(eventType::isInstance)
                .cast(eventType);
    }

    public Flux<DomainEvent> allEvents() {
        return eventFlux;
    }


    public int getSubscriberCount() {
        return eventSink.currentSubscriberCount();
    }
}
