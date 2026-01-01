package biz.ugur.busroutebackend.shared.domain.event;

public interface DomainEventPublisher {

    void publish(DomainEvent event);
}
