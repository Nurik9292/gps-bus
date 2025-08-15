package biz.ugur.busroutebackend.routing.application.dto;

public record TripPlanStats(
        int totalOptions,
        int directOptions,
        int oneTransferOptions,
        int twoTransferOptions,
        int minTravelMinutes,
        int maxTravelMinutes
) {

    public boolean hasAnyOptions() {
        return totalOptions > 0;
    }

    public boolean hasDirectOptions() {
        return directOptions > 0;
    }

    public boolean hasTransferOptions() {
        return oneTransferOptions > 0 || twoTransferOptions > 0;
    }

    public String getSummary() {
        if (totalOptions == 0) {
            return "No routes found";
        }

        return String.format("%d options (direct: %d, 1-transfer: %d, 2-transfer: %d, time: %d-%d min)",
                totalOptions, directOptions, oneTransferOptions, twoTransferOptions,
                minTravelMinutes, maxTravelMinutes);
    }
}