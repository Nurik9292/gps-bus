package biz.ugur.busroutebackend.routing.application.builders;

import biz.ugur.busroutebackend.routing.domain.services.RouteCalculationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@Slf4j
public class TransferRouteValidator {

    public boolean isOneTransferRouteViable(RouteCalculationService.TransferRouteResult route) {
        int totalTime = route.firstRouteTravelMinutes() +
                route.transferWaitMinutes() +
                route.secondRouteTravelMinutes();

        if (totalTime > 100) {
            log.debug("One-transfer route rejected: total time {} > 100 min", totalTime);
            return false;
        }

        if (route.transferWaitMinutes() > 30) {
            log.debug("One-transfer route rejected: wait time {} > 30 min", route.transferWaitMinutes());
            return false;
        }

        if (route.firstRouteTravelMinutes() < 1 || route.secondRouteTravelMinutes() < 1) {
            log.debug("One-transfer route rejected: segments too short");
            return false;
        }

        if (isSameRouteNumber(route.firstRoute().getRouteNumber(), route.secondRoute().getRouteNumber())) {
            log.debug("One-transfer route rejected: same route on both legs ({})",
                    route.firstRoute().getRouteNumber());
            return false;
        }

        return true;
    }

    public boolean isTwoTransferRouteViable(RouteCalculationService.TwoTransferRouteResult route) {
        int totalTime = route.firstRouteTravelMinutes() +
                route.firstTransferWaitMinutes() +
                route.secondRouteTravelMinutes() +
                route.secondTransferWaitMinutes() +
                route.thirdRouteTravelMinutes();

        if (totalTime > 150) {
            log.debug("Two-transfer route rejected: total time {} > 150 min", totalTime);
            return false;
        }

        if (route.firstTransferWaitMinutes() > 25 || route.secondTransferWaitMinutes() > 25) {
            log.debug("Two-transfer route rejected: wait times too long");
            return false;
        }

        if (route.firstRouteTravelMinutes() < 1 ||
                route.secondRouteTravelMinutes() < 1 ||
                route.thirdRouteTravelMinutes() < 1) {
            log.debug("Two-transfer route rejected: segments too short");
            return false;
        }

        if (hasAnyDuplicateRoute(
                route.firstRoute().getRouteNumber(),
                route.secondRoute().getRouteNumber(),
                route.thirdRoute().getRouteNumber())) {
            log.debug("Two-transfer route rejected: duplicate routes");
            return false;
        }

        return true;
    }

    private boolean isSameRouteNumber(String a, String b) {
        return Objects.equals(a, b);
    }

    private boolean hasAnyDuplicateRoute(String... routeNumbers) {
        for (int i = 0; i < routeNumbers.length; i++) {
            for (int j = i + 1; j < routeNumbers.length; j++) {
                if (isSameRouteNumber(routeNumbers[i], routeNumbers[j])) {
                    return true;
                }
            }
        }
        return false;
    }
}
