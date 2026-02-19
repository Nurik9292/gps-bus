package biz.ugur.busroutebackend.admin.application.usecase.analytics;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.RoutingAnalyticsResponse;
import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.RoutingAnalyticsResponse.*;
import biz.ugur.busroutebackend.routing.domain.repository.RoutingAnalyticsRepository;
import biz.ugur.busroutebackend.shared.infrastructure.cache.RedisKeyRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class GetRoutingAnalyticsOverviewUseCase {

    private final RoutingAnalyticsRepository analyticsRepository;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<RoutingAnalyticsResponse> execute(String period, LocalDateTime customFrom, LocalDateTime customTo) {
        String cacheKey = RedisKeyRegistry.Analytics.routingOverview(period);

        return redisTemplate.opsForValue().get(cacheKey)
                .flatMap(cached -> deserialize(cached, RoutingAnalyticsResponse.class))
                .switchIfEmpty(Mono.defer(() -> fetchAndCache(period, customFrom, customTo, cacheKey)));
    }

    private Mono<RoutingAnalyticsResponse> fetchAndCache(String period, LocalDateTime customFrom, LocalDateTime customTo, String cacheKey) {
        LocalDateTime[] range = resolveDateRange(period, customFrom, customTo);
        LocalDateTime from = range[0];
        LocalDateTime to = range[1];

        Mono<OverviewResponse> overviewMono = analyticsRepository.getOverviewStats(from, to)
                .map(stats -> new OverviewResponse(
                        stats.totalSearches(),
                        stats.successfulSearches(),
                        stats.failedSearches(),
                        stats.avgOptionsPerSearch(),
                        stats.avgDurationMinutes(),
                        stats.totalSearches() > 0 ? (double) stats.successfulSearches() / stats.totalSearches() * 100 : 0
                ));

        Mono<List<DailySearchResponse>> dailyMono = analyticsRepository.getDailySearchStats(from, to)
                .map(s -> new DailySearchResponse(s.date(), s.totalSearches(), s.successfulSearches(), s.failedSearches()))
                .collectList();

        Mono<List<HourlySearchResponse>> hourlyMono = analyticsRepository.getHourlySearchStats(from, to)
                .map(s -> new HourlySearchResponse(s.hour(), s.totalSearches(), s.successfulSearches(), s.failedSearches(), s.avgOptions()))
                .collectList();

        Mono<List<TransferDistributionResponse>> transfersMono = analyticsRepository.getTransferDistribution(from, to)
                .map(s -> new TransferDistributionResponse(s.numberOfTransfers(), s.count()))
                .collectList();

        Mono<List<RoutePopularityResponse>> routesMono = analyticsRepository.getRoutePopularity(10, from, to)
                .map(s -> new RoutePopularityResponse(s.routeId(), s.routeNumber(), s.routeName(), s.usageCount()))
                .collectList();

        return Mono.zip(overviewMono, dailyMono, hourlyMono, transfersMono, routesMono)
                .map(tuple -> new RoutingAnalyticsResponse(
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3(),
                        tuple.getT4(),
                        tuple.getT5()
                ))
                .flatMap(response -> cacheAndReturn(cacheKey, response));
    }

    private LocalDateTime[] resolveDateRange(String period, LocalDateTime customFrom, LocalDateTime customTo) {
        LocalDate today = LocalDate.now();
        return switch (period) {
            case "today" -> new LocalDateTime[]{today.atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "week" -> new LocalDateTime[]{today.minusDays(7).atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "month" -> new LocalDateTime[]{today.minusDays(30).atStartOfDay(), today.atTime(LocalTime.MAX)};
            case "custom" -> new LocalDateTime[]{
                    customFrom != null ? customFrom : today.minusDays(30).atStartOfDay(),
                    customTo != null ? customTo : today.atTime(LocalTime.MAX)
            };
            default -> new LocalDateTime[]{today.minusDays(7).atStartOfDay(), today.atTime(LocalTime.MAX)};
        };
    }

    private <T> Mono<T> deserialize(String json, Class<T> type) {
        try {
            return Mono.just(objectMapper.readValue(json, type));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached analytics: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private <T> Mono<T> cacheAndReturn(String cacheKey, T data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            return redisTemplate.opsForValue()
                    .set(cacheKey, json, RedisKeyRegistry.Analytics.OVERVIEW_TTL)
                    .thenReturn(data)
                    .onErrorReturn(data);
        } catch (Exception e) {
            log.warn("Failed to cache analytics: {}", e.getMessage());
            return Mono.just(data);
        }
    }
}
