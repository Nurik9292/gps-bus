package biz.ugur.busroutebackend.routing.domain.events;

import biz.ugur.busroutebackend.shared.domain.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class TripPlanCreatedEvent implements DomainEvent {

    private final String tripPlanId;
    private final Double originLatitude;
    private final Double originLongitude;
    private final Double destinationLatitude;
    private final Double destinationLongitude;
    private final Instant eventOccurredAt;

    public TripPlanCreatedEvent(String tripPlanId, Double originLatitude, Double originLongitude,
                                Double destinationLatitude, Double destinationLongitude) {
        this.tripPlanId = tripPlanId;
        this.originLatitude = originLatitude;
        this.originLongitude = originLongitude;
        this.destinationLatitude = destinationLatitude;
        this.destinationLongitude = destinationLongitude;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("TripPlanCreated[id=%s, from=(%.6f,%.6f), to=(%.6f,%.6f)]",
                tripPlanId, originLatitude, originLongitude, destinationLatitude, destinationLongitude);
    }
}