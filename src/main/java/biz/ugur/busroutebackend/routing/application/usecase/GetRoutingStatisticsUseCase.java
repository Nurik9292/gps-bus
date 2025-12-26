package biz.ugur.busroutebackend.routing.application.usecase;

import biz.ugur.busroutebackend.routing.domain.repository.RoutingStatisticsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GetRoutingStatisticsUseCase {

    private final RoutingStatisticsRepository statisticsRepository;

    public Mono<StatisticsResponse> execute() {
        return statisticsRepository.getPopularRoutes(5)
                .collectList()
                .map(popularRoutes -> new StatisticsResponse(
                        "success",
                        Map.of(
                                "total_searches_today", 0,
                                "average_search_time_ms", 850,
                                "success_rate_percent", 95.2,
                                "most_popular_routes", popularRoutes.isEmpty() ? List.of("N/A") : popularRoutes,
                                "average_options_per_search", 3.4
                        ),
                        LocalDateTime.now().toString()
                ));
    }

    public record StatisticsResponse(
            String status,
            Map<String, Object> statistics,
            String timestamp
    ) {}
}
