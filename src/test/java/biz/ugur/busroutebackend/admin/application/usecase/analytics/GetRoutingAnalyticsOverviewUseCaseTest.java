package biz.ugur.busroutebackend.admin.application.usecase.analytics;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.RoutingAnalyticsResponse;
import biz.ugur.busroutebackend.routing.domain.repository.RoutingAnalyticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetRoutingAnalyticsOverviewUseCaseTest {

    @InjectMocks
    private GetRoutingAnalyticsOverviewUseCase useCase;

    @Mock
    private RoutingAnalyticsRepository analyticsRepository;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @Mock
    private ObjectMapper objectMapper;

    private void stubRepository() {
        RoutingAnalyticsRepository.OverviewStats stats =
                new RoutingAnalyticsRepository.OverviewStats(100, 80, 20, 3.5, 25.0);

        lenient().when(analyticsRepository.getOverviewStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(stats));
        lenient().when(analyticsRepository.getDailySearchStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        lenient().when(analyticsRepository.getHourlySearchStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        lenient().when(analyticsRepository.getTransferDistribution(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        lenient().when(analyticsRepository.getRoutePopularity(anyInt(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
    }

    @Test
    void fetchesFreshForTodayPeriod() throws Exception {
        stubRepository();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("today", null, null))
                .assertNext(response -> assertEquals(100, response.overview().totalSearches()))
                .verifyComplete();
    }

    @Test
    void fetchesFreshForWeekPeriod() throws Exception {
        stubRepository();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("week", null, null))
                .assertNext(response -> assertEquals(80, response.overview().successfulSearches()))
                .verifyComplete();
    }

    @Test
    void fetchesFreshForCustomRange() throws Exception {
        stubRepository();

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        LocalDateTime from = LocalDateTime.now().minusDays(14);
        LocalDateTime to = LocalDateTime.now();

        StepVerifier.create(useCase.execute("custom", from, to))
                .expectNextCount(1)
                .verifyComplete();
    }

    @Test
    void returnsCachedWhenAvailable() throws Exception {
        RoutingAnalyticsResponse cached = new RoutingAnalyticsResponse(
                new RoutingAnalyticsResponse.OverviewResponse(50, 40, 10, 3.0, 20.0, 80.0),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("{\"cached\":\"yes\"}"));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(cached);

        StepVerifier.create(useCase.execute("week", null, null))
                .assertNext(r -> assertEquals(50, r.overview().totalSearches()))
                .verifyComplete();
    }

    @Test
    void computesSuccessRateFromZeroSearches() throws Exception {
        RoutingAnalyticsRepository.OverviewStats zeroStats =
                new RoutingAnalyticsRepository.OverviewStats(0, 0, 0, 0.0, 0.0);

        when(analyticsRepository.getOverviewStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Mono.just(zeroStats));
        when(analyticsRepository.getDailySearchStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        when(analyticsRepository.getHourlySearchStats(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        when(analyticsRepository.getTransferDistribution(any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        when(analyticsRepository.getRoutePopularity(anyInt(), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(Flux.empty());
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("today", null, null))
                .assertNext(r -> assertEquals(0, r.overview().successRate()))
                .verifyComplete();
    }
}
