package biz.ugur.busroutebackend.admin.application.usecase.analytics;

import biz.ugur.busroutebackend.interfaces.rest.admin.V1.response.analytics.ODPairsResponse;
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
class GetODPairsUseCaseTest {

    @InjectMocks
    private GetODPairsUseCase useCase;

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
        RoutingAnalyticsRepository.ODPair pair = new RoutingAnalyticsRepository.ODPair(
                37.96, 58.33, 37.97, 58.34, 100, 3.5);

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.empty());
        when(analyticsRepository.getPopularODPairs(10)).thenReturn(Flux.just(pair));
        when(objectMapper.writeValueAsString(any(ODPairsResponse.class))).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(10))
                .assertNext(response -> assertEquals(1, response.pairs().size()))
                .verifyComplete();
    }

    @Test
    void returnsCachedDataWhenAvailable() throws Exception {
        ODPairsResponse cached = new ODPairsResponse(java.util.List.of());

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("{}"));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenReturn(cached);

        StepVerifier.create(useCase.execute(10))
                .assertNext(response -> assertEquals(0, response.pairs().size()))
                .verifyComplete();
    }

    @Test
    void fetchesFreshWhenDeserializationFails() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get(anyString())).thenReturn(Mono.just("bad"));
        when(objectMapper.readValue(anyString(), any(Class.class))).thenThrow(new RuntimeException("bad"));
        when(analyticsRepository.getPopularODPairs(10)).thenReturn(Flux.empty());
        when(objectMapper.writeValueAsString(any(ODPairsResponse.class))).thenReturn("{}");
        when(valueOps.set(anyString(), anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        StepVerifier.create(useCase.execute(10))
                .assertNext(response -> assertEquals(0, response.pairs().size()))
                .verifyComplete();
    }
}
