package biz.ugur.busroutebackend.routing.application.response;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.TripPlanStats;
import biz.ugur.busroutebackend.routing.domain.enums.TripType;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;

import java.util.List;
import java.util.stream.Collectors;

public class TripPlanExtensions {

    public static List<TripOption> getDirectOptions(TripPlan tripPlan) {
        return tripPlan.getTripOptions().stream()
                .filter(option -> option.getTripType() == TripType.DIRECT)
                .collect(Collectors.toList());
    }

    public static List<TripOption> getOneTransferOptions(TripPlan tripPlan) {
        return tripPlan.getTripOptions().stream()
                .filter(option -> option.getTripType() == TripType.ONE_TRANSFER)
                .collect(Collectors.toList());
    }

    public static List<TripOption> getTwoTransferOptions(TripPlan tripPlan) {
        return tripPlan.getTripOptions().stream()
                .filter(option -> option.getTripType() == TripType.TWO_TRANSFERS)
                .collect(Collectors.toList());
    }

    public static List<TripOption> getTransferOptions(TripPlan tripPlan) {
        return tripPlan.getTripOptions().stream()
                .filter(option -> option.getTripType() != TripType.DIRECT)
                .collect(Collectors.toList());
    }

    public static List<TripOption> getBestOptionsSorted(TripPlan tripPlan, int limit) {
        return tripPlan.getTripOptions().stream()
                .sorted(new TripOptionComparator())
                .limit(limit)
                .collect(Collectors.toList());
    }

    public static TripPlan empty(SearchContext context) {
        return new TripPlan(
                TripPlanId.generate(),
                context.fromLocation(),
                context.toLocation(),
                context.searchCriteria()
        );
    }

    public static boolean isEmpty(TripPlan tripPlan) {
        return tripPlan.getTripOptions().isEmpty();
    }

    public static TripPlanStats getStats(TripPlan tripPlan) {
        List<TripOption> options = tripPlan.getTripOptions();

        return new TripPlanStats(
                options.size(),
                (int) options.stream().filter(o -> o.getTripType() == TripType.DIRECT).count(),
                (int) options.stream().filter(o -> o.getTripType() == TripType.ONE_TRANSFER).count(),
                (int) options.stream().filter(o -> o.getTripType() == TripType.TWO_TRANSFERS).count(),
                options.stream().mapToInt(TripOption::getTotalTravelMinutes).min().orElse(0),
                options.stream().mapToInt(TripOption::getTotalTravelMinutes).max().orElse(0)
        );
    }
}