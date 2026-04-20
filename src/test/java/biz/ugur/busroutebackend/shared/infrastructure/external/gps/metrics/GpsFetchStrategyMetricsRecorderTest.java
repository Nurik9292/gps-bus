package biz.ugur.busroutebackend.shared.infrastructure.external.gps.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GpsFetchStrategyMetricsRecorderTest {

    @Mock
    private ReactiveRedisTemplate<String, Object> redisTemplate;

    @Mock
    private ReactiveValueOperations<String, Object> valueOps;

    @InjectMocks
    private GpsFetchStrategyMetricsRecorder recorder;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.increment(anyString(), anyLong())).thenReturn(Mono.just(1L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));
    }

    @Test
    void recordSelectiveFetchTracksCountAndDevices() {
        recorder.recordSelectiveFetch(10, "tugdk");
        recorder.recordSelectiveFetch(5, "tugdk");

        GpsFetchStrategyMetricsRecorder.GpsFetchStats stats = recorder.getStats();
        assertEquals(2, stats.selectiveFetchCount());
        assertEquals(15, stats.selectiveFetchDevices());
    }

    @Test
    void recordBulkFetchTracksCountAndRequestedDevices() {
        recorder.recordBulkFetch(100, "china");

        GpsFetchStrategyMetricsRecorder.GpsFetchStats stats = recorder.getStats();
        assertEquals(1, stats.bulkFetchCount());
        assertEquals(100, stats.bulkFetchDevices());
    }

    @Test
    void recordCacheHitTracksCount() {
        recorder.recordCacheHit(3, "tugdk");
        recorder.recordCacheHit(2, "tugdk");

        GpsFetchStrategyMetricsRecorder.GpsFetchStats stats = recorder.getStats();
        assertEquals(2, stats.cacheHitCount());
    }

    @Test
    void statsComputeRatiosAndAverages() {
        recorder.recordSelectiveFetch(12, "tugdk");
        recorder.recordSelectiveFetch(8, "tugdk");
        recorder.recordBulkFetch(100, "tugdk");
        recorder.recordCacheHit(5, "tugdk");

        GpsFetchStrategyMetricsRecorder.GpsFetchStats stats = recorder.getStats();
        assertEquals(4, stats.totalFetches());
        assertEquals(10.0, stats.avgDevicesPerSelectiveFetch());
        assertEquals(100.0, stats.avgDevicesPerBulkFetch());
    }

    @Test
    void selectiveRatioReflectsApiCallMix() {
        recorder.recordSelectiveFetch(5, "tugdk");
        recorder.recordSelectiveFetch(5, "tugdk");
        recorder.recordBulkFetch(100, "tugdk");

        assertEquals(200.0 / 3, recorder.getStats().selectiveRatio(), 0.01);
    }

    @Test
    void emptyStatsReportZeroRatios() {
        GpsFetchStrategyMetricsRecorder.GpsFetchStats stats = recorder.getStats();
        assertEquals(0.0, stats.selectiveRatio());
        assertEquals(0.0, stats.cacheHitRatio());
        assertEquals(0.0, stats.avgDevicesPerSelectiveFetch());
        assertEquals(0.0, stats.avgDevicesPerBulkFetch());
    }

    @Test
    void resetCountersClearsAll() {
        recorder.recordSelectiveFetch(10, "tugdk");
        recorder.recordBulkFetch(20, "tugdk");
        recorder.resetCounters();

        GpsFetchStrategyMetricsRecorder.GpsFetchStats stats = recorder.getStats();
        assertEquals(0, stats.totalFetches());
    }
}
