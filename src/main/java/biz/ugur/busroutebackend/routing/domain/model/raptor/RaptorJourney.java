package biz.ugur.busroutebackend.routing.domain.model.raptor;

import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;

import java.util.List;

public record RaptorJourney(BusStopId fromStop,
                             BusStopId toStop,
                             int departureTimeSec,
                             int arrivalTimeSec,
                             int numTransfers,
                             List<RaptorLeg> legs) {

    public RaptorJourney {
        if (fromStop == null || toStop == null) {
            throw new IllegalArgumentException("RaptorJourney endpoints required");
        }
        if (legs == null || legs.isEmpty()) {
            throw new IllegalArgumentException("RaptorJourney must have at least one leg");
        }
        if (arrivalTimeSec < departureTimeSec) {
            throw new IllegalArgumentException(
                    "RaptorJourney arrivalTimeSec (" + arrivalTimeSec
                            + ") < departureTimeSec (" + departureTimeSec + ")");
        }
        if (numTransfers < 0) {
            throw new IllegalArgumentException(
                    "RaptorJourney numTransfers must be >= 0, got " + numTransfers);
        }
        legs = List.copyOf(legs);
    }

    public int totalTravelSec() {
        return arrivalTimeSec - departureTimeSec;
    }

    public long busLegCount() {
        return legs.stream().filter(l -> l.type() == RaptorLeg.LegType.BUS).count();
    }

    public int totalWalkingSec() {
        return legs.stream()
                .filter(l -> l.type() == RaptorLeg.LegType.WALK)
                .mapToInt(RaptorLeg::durationSec)
                .sum();
    }
}
