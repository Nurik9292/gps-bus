package biz.ugur.busroutebackend.routing.domain.events;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class TripOptionsCalculatedEvent implements DomainEvent {

    private final String tripPlanId;
    private final int totalOptionsCount;
    private final String optionType;
    private final int estimatedTravelMinutes;
    private final Instant eventOccurredAt;

    public TripOptionsCalculatedEvent(String tripPlanId, int totalOptionsCount,
                                      String optionType, int estimatedTravelMinutes) {
        this.tripPlanId = tripPlanId;
        this.totalOptionsCount = totalOptionsCount;
        this.optionType = optionType;
        this.estimatedTravelMinutes = estimatedTravelMinutes;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("TripOptionsCalculated[id=%s, count=%d, type=%s, time=%d min]",
                tripPlanId, totalOptionsCount, optionType, estimatedTravelMinutes);
    }
}