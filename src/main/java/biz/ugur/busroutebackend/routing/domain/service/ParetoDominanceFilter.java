package biz.ugur.busroutebackend.routing.domain.service;

import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;

import java.util.ArrayList;
import java.util.List;

public class ParetoDominanceFilter {

    public List<TripOption> filterDominated(List<TripOption> options) {
        if (options == null) {
            return List.of();
        }
        if (options.size() <= 1) {
            return new ArrayList<>(options);
        }

        List<TripOption> survivors = new ArrayList<>();
        for (TripOption candidate : options) {
            if (isWalkingOnly(candidate) || isNotDominatedByAny(candidate, options)) {
                survivors.add(candidate);
            }
        }
        return survivors;
    }

    private boolean isWalkingOnly(TripOption option) {
        return option.getTripType() == TripType.WALKING_ONLY;
    }

    private boolean isNotDominatedByAny(TripOption candidate, List<TripOption> options) {
        for (TripOption other : options) {
            if (other != candidate && dominates(other, candidate)) {
                return false;
            }
        }
        return true;
    }

    private boolean dominates(TripOption dominator, TripOption candidate) {
        boolean noWorseOnAnyAxis =
                dominator.getTotalTravelMinutes() <= candidate.getTotalTravelMinutes()
                        && dominator.getTransfersCount() <= candidate.getTransfersCount()
                        && dominator.getTotalWalkingMinutes() <= candidate.getTotalWalkingMinutes();

        boolean strictlyBetterOnSomeAxis =
                dominator.getTotalTravelMinutes() < candidate.getTotalTravelMinutes()
                        || dominator.getTransfersCount() < candidate.getTransfersCount()
                        || dominator.getTotalWalkingMinutes() < candidate.getTotalWalkingMinutes();

        return noWorseOnAnyAxis && strictlyBetterOnSomeAxis;
    }
}
