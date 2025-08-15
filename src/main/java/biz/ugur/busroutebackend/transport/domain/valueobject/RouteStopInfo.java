package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;
import java.util.Objects;

@Getter
@ToString
@EqualsAndHashCode(callSuper = false)
public class RouteStopInfo extends ValueObject {

    private final String stopId;
    private final String stopName;
    private final String stopCode;
    private final Integer sequence;
    private final Integer direction;
    private final Integer estimatedTravelTimeMinutes;
    private final Integer distanceFromStartMeters;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final Boolean isMajorStop;

    public RouteStopInfo(String stopId,
                         String stopName,
                         String stopCode,
                         Integer sequence,
                         Integer direction,
                         Integer estimatedTravelTimeMinutes,
                         Integer distanceFromStartMeters,
                         BigDecimal latitude,
                         BigDecimal longitude,
                         Boolean isMajorStop) {
        this.stopId = Objects.requireNonNull(stopId, "Stop ID cannot be null");
        this.stopName = Objects.requireNonNull(stopName, "Stop name cannot be null");
        this.stopCode = stopCode;
        this.sequence = sequence;
        this.direction = direction;
        this.estimatedTravelTimeMinutes = estimatedTravelTimeMinutes;
        this.distanceFromStartMeters = distanceFromStartMeters;
        this.latitude = Objects.requireNonNull(latitude, "Latitude cannot be null");
        this.longitude = Objects.requireNonNull(longitude, "Longitude cannot be null");
        this.isMajorStop = isMajorStop != null ? isMajorStop : false;
    }
}