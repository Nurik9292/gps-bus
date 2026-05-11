package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

public record StopTime(TripId tripId,
                       int stopSequence,
                       BusStopId stopId,
                       int arrivalOffsetSec,
                       int departureOffsetSec) {

    public StopTime {
        if (tripId == null) {
            throw new IllegalArgumentException("StopTime tripId is required");
        }
        if (stopId == null) {
            throw new IllegalArgumentException("StopTime stopId is required");
        }
        if (stopSequence < 1) {
            throw new IllegalArgumentException(
                    "StopTime stopSequence must be >= 1, got " + stopSequence);
        }
        if (arrivalOffsetSec < 0) {
            throw new IllegalArgumentException(
                    "StopTime arrivalOffsetSec must be >= 0, got " + arrivalOffsetSec);
        }
        if (departureOffsetSec < arrivalOffsetSec) {
            throw new IllegalArgumentException(
                    "StopTime departureOffsetSec (" + departureOffsetSec
                            + ") must be >= arrivalOffsetSec (" + arrivalOffsetSec + ")");
        }
    }

    public static StopTime instantTransit(TripId tripId,
                                           int stopSequence,
                                           BusStopId stopId,
                                           int offsetSec) {
        return new StopTime(tripId, stopSequence, stopId, offsetSec, offsetSec);
    }

    public int dwellSec() {
        return departureOffsetSec - arrivalOffsetSec;
    }
}
