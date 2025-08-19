package biz.ugur.busroutebackend.shared.application;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EventBus {

    private final ApplicationEventPublisher applicationEventPublisher;

    public EventBus(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }


    public void publish(DomainEvent event) {
        applicationEventPublisher.publishEvent(event);
    }


    public void publish(DomainEvent... events) {
        for (DomainEvent event : events) {
            publish(event);
        }
    }

    public void publishAll(List<DomainEvent> events) {
        if (events != null && !events.isEmpty()) {
            events.forEach(this::publish);
        }
    }
}