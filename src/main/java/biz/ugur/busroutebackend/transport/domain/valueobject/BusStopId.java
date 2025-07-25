package biz.ugur.busroutebackend.transport.domain.valueobject;

import biz.ugur.busroutebackend.shared.domain.ValueObject;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.UUID;

@Getter
@EqualsAndHashCode(callSuper = false)
public class BusStopId extends ValueObject {

    private final String value;

    public BusStopId(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Bus Stop ID cannot be null or empty");
        }
        this.value = value.trim();
    }

    public static BusStopId generate() {
        return new BusStopId(UUID.randomUUID().toString());
    }

    public static BusStopId of(String value) {
        return new BusStopId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}