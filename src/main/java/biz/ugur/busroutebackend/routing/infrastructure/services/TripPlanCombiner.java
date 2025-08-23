package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.util.function.Tuple3;

@Component
@Slf4j
public class TripPlanCombiner {

    public TripPlan combine(SearchContext context, Tuple3<SearchResult, SearchResult, SearchResult> results) {
        SearchResult directResult = results.getT1();
        SearchResult oneTransferResult = results.getT2();
        SearchResult twoTransferResult = results.getT3();

        TripPlan combinedPlan = new TripPlan(TripPlanId.generate(),
                context.fromLocation(),
                context.toLocation(),
                context.searchCriteria());

        addOptionsFromResult(combinedPlan, directResult, "direct");
        addOptionsFromResult(combinedPlan, oneTransferResult, "one-transfer");
        addOptionsFromResult(combinedPlan, twoTransferResult, "two-transfer");

        log.info("Combined plan created with {} total options", combinedPlan.getTripOptions().size());
        return combinedPlan;
    }

    private void addOptionsFromResult(TripPlan plan, SearchResult result, String type) {
        if (result.isSuccessful()) {
            int addedCount = 0;
            for (TripOption option : result.getOptions()) {
                try {
                    plan.addTripOption(option);
                    addedCount++;
                } catch (Exception e) {
                    log.warn("Failed to add {} option: {}", type, e.getMessage());
                }
            }
            log.debug("Added {}/{} {} options", addedCount, result.getOptions().size(), type);
        } else {
            log.warn("Skipping failed {} search: {}", type, result.getErrorMessage());
        }
    }
}