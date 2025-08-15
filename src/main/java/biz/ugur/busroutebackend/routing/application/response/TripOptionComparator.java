package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.routing.domain.volumeojects.TripOption;

import java.util.Comparator;

public class TripOptionComparator implements Comparator<TripOption> {

    @Override
    public int compare(TripOption o1, TripOption o2) {
        int transferCompare = Integer.compare(o1.getTransfersCount(), o2.getTransfersCount());
        if (transferCompare != 0) return transferCompare;

        int timeCompare = Integer.compare(o1.getTotalTravelMinutes(), o2.getTotalTravelMinutes());
        if (timeCompare != 0) return timeCompare;

        int walkingCompare = Integer.compare(o1.getTotalWalkingMinutes(), o2.getTotalWalkingMinutes());
        if (walkingCompare != 0) return walkingCompare;

        return o1.getOptionId().compareTo(o2.getOptionId());
    }
}
