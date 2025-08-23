package biz.ugur.busroutebackend.routing.domain.repository;

import biz.ugur.busroutebackend.routing.domain.model.TripPlan;
import biz.ugur.busroutebackend.routing.domain.valueobjects.TripPlanId;
import biz.ugur.busroutebackend.shared.base.BaseRepository;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;

public interface TripPlanRepository extends BaseRepository<TripPlan, TripPlanId> {

    Flux<TripPlan> findRecentPlans(int limit);

    Flux<TripPlan> findPlansByTimeRange(LocalDateTime from, LocalDateTime to);

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