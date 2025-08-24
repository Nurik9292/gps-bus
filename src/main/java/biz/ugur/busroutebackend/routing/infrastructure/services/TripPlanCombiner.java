package biz.ugur.busroutebackend.routing.infrastructure.services;

import biz.ugur.busroutebackend.routing.application.dto.SearchContext;
import biz.ugur.busroutebackend.routing.application.dto.SearchResult;
import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripOption;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.util.function.Tuple3;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class TripPlanCombiner {

    public TripPlan combine(SearchContext context,
                            Tuple3<SearchResult, SearchResult, SearchResult> results) {

        SearchResult directResult = results.getT1();
        SearchResult oneTransferResult = results.getT2();
        SearchResult twoTransferResult = results.getT3();

        List<TripOption> allOptions = collectAllOptions(
                List.of(directResult, oneTransferResult, twoTransferResult)
        );

        return createTripPlan(context, allOptions);
    }

    public TripPlan combineWithDeduplication(SearchContext context,
                                             List<SearchResult> allResults,
                                             List<TripOption> uniqueRoutes) {

        TripPlan tripPlan = createTripPlan(context, uniqueRoutes);

        logDeduplicationResults(context, allResults, uniqueRoutes);

        return tripPlan;
    }

    private List<TripOption> collectAllOptions(List<SearchResult> searchResults) {
        return searchResults.stream()
                .filter(SearchResult::isSuccessful)
                .flatMap(result -> result.getOptions().stream())
                .collect(Collectors.toList());
    }

    private TripPlan createTripPlan(SearchContext context, List<TripOption> options) {

        TripPlan tripPlan = new TripPlan(
                TripPlanId.generate(),
                context.fromLocation(),
                context.toLocation(),
                context.searchCriteria()
        );


        options.forEach(tripPlan::addTripOption);

        return tripPlan;
    }

    private void logDeduplicationResults(SearchContext context,
                                         List<SearchResult> allResults,
                                         List<TripOption> uniqueRoutes) {

        int totalOriginalRoutes = allResults.stream()
                .filter(SearchResult::isSuccessful)
                .mapToInt(result -> result.getOptions().size())
                .sum();

        int duplicatesRemoved = totalOriginalRoutes - uniqueRoutes.size();
        double deduplicationRate = totalOriginalRoutes > 0 ?
                (double) duplicatesRemoved / totalOriginalRoutes * 100.0 : 0.0;


        Map<String, Integer> resultsByType = allResults.stream()
                .filter(SearchResult::isSuccessful)
                .collect(Collectors.toMap(
                        SearchResult::getSearchType,
                        result -> result.getOptions().size(),
                        Integer::sum
                ));

        log.info("[{}] Trip plan created: {} unique routes from {} total (removed {} duplicates - {:.1f}%)",
                context.searchId(),
                uniqueRoutes.size(),
                totalOriginalRoutes,
                duplicatesRemoved,
                deduplicationRate);

        log.debug("[{}] Results breakdown: {}", context.searchId(), resultsByType);


        Map<Integer, Long> routesByTransfers = uniqueRoutes.stream()
                .collect(Collectors.groupingBy(
                        TripOption::getTransfersCount,
                        Collectors.counting()
                ));

        log.debug("[{}] Routes by transfers: {}", context.searchId(), routesByTransfers);
    }
}