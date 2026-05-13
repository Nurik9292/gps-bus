package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderStatusTest {

    private static final Instant NOW = Instant.parse("2026-05-12T10:00:00Z");

    @Test
    void initialStateIsOkWithNoFailures() {
        ProviderStatus s = ProviderStatus.initial();
        assertEquals(ProviderStatus.State.OK, s.state());
        assertEquals(0, s.consecutiveFailures());
        assertEquals(0, s.consecutiveEmpty());
        assertEquals(0, s.consecutiveClear());
        assertEquals(0, s.lastDeviceCount());
        assertEquals(0, s.baselineDeviceCount());
        assertNull(s.degradedReason());
    }

    @Test
    void recordSuccessResetsFailuresAndUpdatesBaseline() {
        ProviderStatus s0 = ProviderStatus.initial()
                .recordError(NOW)
                .recordError(NOW);
        assertEquals(2, s0.consecutiveFailures());

        ProviderStatus s1 = s0.recordSuccess(NOW, 50, 50, NOW, Duration.ofHours(24), 10);
        assertEquals(0, s1.consecutiveFailures());
        assertEquals(50, s1.lastDeviceCount());
        assertEquals(50, s1.baselineDeviceCount());
        assertEquals(NOW, s1.lastSuccessfulFetchAt());
    }

    @Test
    void baselineKeepsMaxWithinWindow() {
        Instant t0 = NOW;
        ProviderStatus s = ProviderStatus.initial()
                .recordSuccess(t0,                       30, 30, t0, Duration.ofHours(24), 10)
                .recordSuccess(t0.plus(Duration.ofMinutes(5)),  60, 60, t0.plus(Duration.ofMinutes(5)), Duration.ofHours(24), 10)
                .recordSuccess(t0.plus(Duration.ofMinutes(10)), 45, 45, t0.plus(Duration.ofMinutes(10)), Duration.ofHours(24), 10);
        assertEquals(60, s.baselineDeviceCount());
        assertEquals(45, s.lastDeviceCount());
    }

    @Test
    void baselineExpiresAfterWindow() {
        Instant t0 = NOW;
        ProviderStatus s = ProviderStatus.initial()
                .recordSuccess(t0, 100, 100, t0, Duration.ofHours(24), 10);
        assertEquals(100, s.baselineDeviceCount());

        ProviderStatus later = s.recordSuccess(
                t0.plus(Duration.ofHours(25)), 30, 30, t0.plus(Duration.ofHours(25)),
                Duration.ofHours(24), 10);
        assertEquals(30, later.baselineDeviceCount());
    }

    @Test
    void recordEmptyIncrementsCounterClearsFailures() {
        ProviderStatus s = ProviderStatus.initial()
                .recordError(NOW)
                .recordEmpty(NOW);
        assertEquals(0, s.consecutiveFailures());
        assertEquals(1, s.consecutiveEmpty());
    }

    @Test
    void recordErrorIncrementsCounterClearsEmpty() {
        ProviderStatus s = ProviderStatus.initial()
                .recordEmpty(NOW)
                .recordError(NOW);
        assertEquals(0, s.consecutiveEmpty());
        assertEquals(1, s.consecutiveFailures());
    }

    @Test
    void markDegradedSetsReasonAndSince() {
        ProviderStatus s = ProviderStatus.initial()
                .markDegraded(AlertKind.HTTP_ERROR, NOW);
        assertEquals(ProviderStatus.State.DEGRADED, s.state());
        assertEquals(AlertKind.HTTP_ERROR, s.degradedReason());
        assertEquals(NOW, s.degradedSince());
        assertTrue(s.consecutiveClear() == 0);
    }

    @Test
    void markRecoveredResetsState() {
        ProviderStatus s = ProviderStatus.initial()
                .markDegraded(AlertKind.HTTP_ERROR, NOW)
                .markRecovered();
        assertEquals(ProviderStatus.State.OK, s.state());
        assertNull(s.degradedReason());
        assertNull(s.degradedSince());
    }
}
