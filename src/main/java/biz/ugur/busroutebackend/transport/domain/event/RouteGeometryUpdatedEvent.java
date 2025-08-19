package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;

import java.time.Instant;

@Getter
public class RouteGeometryUpdatedEvent implements DomainEvent {

    private final String routeId;
    private final String routeNumber;
    private final String routeName;
    private final Integer forwardPointsCount;
    private final Integer forwardDistanceMeters;
    private final Integer backwardPointsCount;
    private final Integer backwardDistanceMeters;
    private final Instant eventOccurredAt;

    public RouteGeometryUpdatedEvent(
            String routeId,
            String routeNumber,
            String routeName,
            Integer forwardPointsCount,
            Integer forwardDistanceMeters,
            Integer backwardPointsCount,
            Integer backwardDistanceMeters) {
        this.routeId = routeId;
        this.routeNumber = routeNumber;
        this.routeName = routeName;
        this.forwardPointsCount = forwardPointsCount;
        this.forwardDistanceMeters = forwardDistanceMeters;
        this.backwardPointsCount = backwardPointsCount;
        this.backwardDistanceMeters = backwardDistanceMeters;
        this.eventOccurredAt = Instant.now();
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }

    @Override
    public String toString() {
        return String.format("RouteGeometryUpdated[route=%s (%s), forward=%d points/%.1fkm, backward=%d points/%.1fkm]",
                routeNumber, routeName, forwardPointsCount, forwardDistanceMeters/1000.0,
                backwardPointsCount, backwardDistanceMeters != null ? backwardDistanceMeters/1000.0 : 0.0);
    }
}