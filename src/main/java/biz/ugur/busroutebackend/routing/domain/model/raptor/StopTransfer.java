package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

public record StopTransfer(BusStopId fromStopId,
                            BusStopId toStopId,
                            int walkingSeconds,
                            int distanceMeters,
                            TransferType transferType) {

    public StopTransfer {
        if (fromStopId == null) {
            throw new IllegalArgumentException("StopTransfer fromStopId is required");
        }
        if (toStopId == null) {
            throw new IllegalArgumentException("StopTransfer toStopId is required");
        }
        if (fromStopId.equals(toStopId)) {
            throw new IllegalArgumentException(
                    "StopTransfer fromStopId and toStopId must differ: " + fromStopId);
        }
        if (walkingSeconds <= 0) {
            throw new IllegalArgumentException(
                    "StopTransfer walkingSeconds must be > 0, got " + walkingSeconds);
        }
        if (distanceMeters <= 0) {
            throw new IllegalArgumentException(
                    "StopTransfer distanceMeters must be > 0, got " + distanceMeters);
        }
        if (transferType == null) {
            throw new IllegalArgumentException("StopTransfer transferType is required");
        }
    }

    public static StopTransfer footpath(BusStopId from,
                                         BusStopId to,
                                         int walkingSeconds,
                                         int distanceMeters) {
        return new StopTransfer(from, to, walkingSeconds, distanceMeters, TransferType.FOOTPATH);
    }
}
