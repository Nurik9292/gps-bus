package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

public record RaptorTransfer(BusStopId fromStopId,
                              BusStopId toStopId,
                              int walkingSeconds,
                              int distanceMeters) {

    public RaptorTransfer {
        if (fromStopId == null || toStopId == null) {
            throw new IllegalArgumentException("RaptorTransfer endpoints required");
        }
        if (fromStopId.equals(toStopId)) {
            throw new IllegalArgumentException(
                    "RaptorTransfer endpoints must differ: " + fromStopId);
        }
        if (walkingSeconds <= 0) {
            throw new IllegalArgumentException(
                    "RaptorTransfer walkingSeconds must be > 0, got " + walkingSeconds);
        }
        if (distanceMeters <= 0) {
            throw new IllegalArgumentException(
                    "RaptorTransfer distanceMeters must be > 0, got " + distanceMeters);
        }
    }
}
