package biz.ugur.busroutebackend.routing.domain.repository;

import reactor.core.publisher.Flux;

public interface RoutingStatisticsRepository {

    Flux<String> getPopularRoutes(int limit);
}
