package biz.ugur.busroutebackend.routing.domain.model.raptor;

import lombok.Getter;

@Getter
public enum TransferType {
    FOOTPATH(0),
    SAME_STATION(1),
    TIMED_TRANSFER(2);

    private final int value;

    TransferType(int value) {
        this.value = value;
    }

    public static TransferType fromValue(int value) {
        return switch (value) {
            case 0 -> FOOTPATH;
            case 1 -> SAME_STATION;
            case 2 -> TIMED_TRANSFER;
            default -> throw new IllegalArgumentException("Invalid transfer_type: " + value);
        };
    }
}
