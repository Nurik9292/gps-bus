package biz.ugur.busroutebackend.transport.infrastructure.cache;

import biz.ugur.busroutebackend.transport.application.dto.VehiclePositionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.ReactiveListOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ActiveVehicleCacheRepositoryTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    private ActiveVehicleCacheRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ActiveVehicleCacheRepository(redisTemplate, new ObjectMapper());

        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.set(anyString(), any(), any(Duration.class))).thenReturn(Mono.just(true));

        ReactiveListOperations<String, Object> listOps = mock(ReactiveListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOps);
        when(redisTemplate.delete(anyString())).thenReturn(Mono.just(1L));
        when(listOps.rightPushAll(anyString(), any(Object[].class))).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    }

    private VehiclePositionDTO vehicle(String id) {
        VehiclePositionDTO v = new VehiclePositionDTO();
        v.setVehicleId(id);
        return v;
    }

    @Test
    void cacheWritesVehiclesWithTtlViaSingleAtomicSet() {
        Duration ttl = Duration.ofSeconds(20);

        StepVerifier.create(
                repository.cache("active_vehicles:route:5", Flux.just(vehicle("veh-1")), ttl)
        ).verifyComplete();

        verify(valueOps).set(eq("active_vehicles:route:5"), any(), eq(ttl));
    }

    @Test
    void cacheSkipsWriteForEmptyList() {
        StepVerifier.create(
                repository.cache("active_vehicles:all", Flux.empty(), Duration.ofSeconds(30))
        ).verifyComplete();

        verify(valueOps, never()).set(anyString(), any(), any(Duration.class));
    }

    @Test
    void getCachedReturnsEmptyWhenBackingReadFails() {
        when(valueOps.get(anyString())).thenReturn(Mono.error(new IllegalStateException("WRONGTYPE")));

        StepVerifier.create(repository.getCached("active_vehicles:route:5"))
                .verifyComplete();
    }
}
