package biz.ugur.busroutebackend.transport.domain.event;

import biz.ugur.busroutebackend.shared.domain.event.DomainEvent;
import lombok.Getter;
import lombok.ToString;
import org.springframework.context.ApplicationEvent;

import java.time.Instant;

@ToString
@Getter
public class VehiclePositionUpdatedEvent implements DomainEvent{

    private final String vehicleId;
    private final String deviceId;
    private final String licensePlate;
    private final String routeNumber;
    private final Double latitude;
    private final Double longitude;
    private final Double speedKmh;
    private final Boolean isInMotion;
    private final Instant positionTimestamp;
    private final Instant eventOccurredAt;
    private final Double course;

    public VehiclePositionUpdatedEvent(String vehicleId, String deviceId, String licensePlate,
                                       String routeNumber,
                                       Double latitude, Double longitude, Double speedKmh,
                                       Boolean isInMotion, Instant positionTimestamp, Double course) {
        this.vehicleId = vehicleId;
        this.deviceId = deviceId;
        this.licensePlate = licensePlate;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speedKmh = speedKmh;
        this.isInMotion = isInMotion;
        this.positionTimestamp = positionTimestamp;
        this.eventOccurredAt = Instant.now();
        this.routeNumber = routeNumber;
        this.course = course;
    }

    @Override
    public Instant getOccurredAt() {
        return eventOccurredAt;
    }


}
