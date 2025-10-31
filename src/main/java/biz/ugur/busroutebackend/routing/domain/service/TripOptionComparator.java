package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripSearchCriteria;

import java.util.Comparator;

public class TripOptionComparator implements Comparator<TripOption> {

    private final TripSearchCriteria searchCriteria;

    public TripOptionComparator(TripSearchCriteria searchCriteria) {
        this.searchCriteria = searchCriteria != null ? searchCriteria : TripSearchCriteria.defaultCriteria();
    }

    @Override
    public int compare(TripOption a, TripOption b) {
        if (searchCriteria.isPrioritizeFewerTransfers()) {
            int transfersComparison = Integer.compare(a.getTransfersCount(), b.getTransfersCount());
            if (transfersComparison != 0) return transfersComparison;
        }

        if (searchCriteria.isPrioritizeSpeed()) {
            int timeComparison = Integer.compare(a.getTotalTravelMinutes(), b.getTotalTravelMinutes());
            if (timeComparison != 0) return timeComparison;
        }

        if (!searchCriteria.isPrioritizeFewerTransfers()) {
            int transfersComparison = Integer.compare(a.getTransfersCount(), b.getTransfersCount());
            if (transfersComparison != 0) return transfersComparison;
        }

        if (!searchCriteria.isPrioritizeSpeed()) {
            int timeComparison = Integer.compare(a.getTotalTravelMinutes(), b.getTotalTravelMinutes());
            if (timeComparison != 0) return timeComparison;
        }

        return Integer.compare(a.getTotalWalkingMinutes(), b.getTotalWalkingMinutes());
    }

    public boolean isOptionBetter(TripOption option1, TripOption option2) {
        return compare(option1, option2) < 0;
    }
}
