package biz.ugur.busroutebackend.routing.domain.repository;

import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.volumeojects.TripPlanId;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

public interface TripPlanRepository {

    Mono<TripPlan> save(TripPlan tripPlan);

    Mono<TripPlan> findById(TripPlanId tripPlanId);

    Flux<TripPlan> findRecentPlans(int limit);

    Flux<TripPlan> findPlansByTimeRange(LocalDateTime from, LocalDateTime to);

    Mono<Void> deleteById(TripPlanId tripPlanId);

    Mono<Long> countTotalPlans();

    // Analytics queries
    Flux<TripPlanningStatistics> getTripPlanningStatistics(LocalDateTime from, LocalDateTime to);

    record TripPlanningStatistics(
            LocalDateTime date,
            long totalSearches,
            long successfulSearches,
            double averageOptionsFound,
            double averageTravelTime,
            String mostPopularRoute
    ) {}
}