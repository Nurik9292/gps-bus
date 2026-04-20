package biz.ugur.busroutebackend.admin.application.usecase.analytics;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.HeatmapResponse;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetHeatmapDataUseCaseTest {

    @InjectMocks
    private GetHeatmapDataUseCase useCase;

    @Mock
    private RoutingAnalyticsRepository analyticsRepository;

    @Mock
    private ReactiveStringRedisTemplate redisTemplate;

    @Mock
    private ReactiveValueOperations<String, String> valueOps;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    void fetchesFromRepositoryWhenCacheEmpty() throws Exception {
        RoutingAnalyticsRepository.HeatmapPoint point = new RoutingAnalyticsRepository.HeatmapPoint(
                37.96, 58.33, 42, "origin");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(analyticsRepository.getHeatmapData("origin")).thenReturn(Flux.just(point));
        when(objectMapper.writeValueAsString(any(HeatmapResponse.class))).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("origin"))
                .assertNext(response -> {
                    assertEquals(1, response.points().size());
                    assertEquals("origin", response.pointType());
                })
                .verifyComplete();
    }

    @Test
    void returnsCachedDataWhenAvailable() throws Exception {
        HeatmapResponse cached = new HeatmapResponse(java.util.List.of(), "destination");

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("{\"cache\":\"hit\"}"));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(cached);

        StepVerifier.create(useCase.execute("destination"))
                .assertNext(response -> assertEquals("destination", response.pointType()))
                .verifyComplete();
    }

    @Test
    void fetchesFreshWhenDeserializationFails() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("corrupted"));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenThrow(new RuntimeException("bad json"));
        when(analyticsRepository.getHeatmapData("origin")).thenReturn(Flux.empty());
        when(objectMapper.writeValueAsString(any(HeatmapResponse.class))).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute("origin"))
                .assertNext(response -> assertEquals(0, response.points().size()))
                .verifyComplete();
    }
}
