package biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring;

import java.time.Duration;
import java.time.Instant;

public record ProviderStatus(
        State state,
        AlertKind degradedReason,
        Instant degradedSince,
        int consecutiveFailures,
        int consecutiveEmpty,
        int consecutiveClear,
        int lastDeviceCount,
        int lastFreshCount,
        int baselineDeviceCount,
        Instant baselineSetAt,
        Instant lastSuccessfulFetchAt,
        Instant latestFixTime,
        Throwable lastError
) {

    public enum State { OK, DEGRADED }

    public static ProviderStatus initial() {
        return new ProviderStatus(State.OK, null, null,
                0, 0, 0,
                0, 0, 0, null,
                null, null, null);
    }

    public ProviderStatus recordSuccess(Instant fetchAt,
                                         int deviceCount,
                                         int freshCount,
                                         Instant latestFix,
                                         Duration baselineWindow,
                                         int minBaseline) {
        int newBaseline;
        Instant newBaselineAt;
        boolean baselineExpired = baselineSetAt == null
                || Duration.between(baselineSetAt, fetchAt).compareTo(baselineWindow) > 0;
        if (baselineExpired) {
            newBaseline = deviceCount;
            newBaselineAt = fetchAt;
        } else if (deviceCount > baselineDeviceCount) {
            newBaseline = deviceCount;
            newBaselineAt = fetchAt;
        } else {
            newBaseline = baselineDeviceCount;
            newBaselineAt = baselineSetAt;
        }
        return new ProviderStatus(state, degradedReason, degradedSince,
                0, 0, consecutiveClear,
                deviceCount, freshCount, newBaseline, newBaselineAt,
                fetchAt, latestFix, null);
    }

    public ProviderStatus recordEmpty(Instant fetchAt) {
        return new ProviderStatus(state, degradedReason, degradedSince,
                0, consecutiveEmpty + 1, 0,
                0, 0, baselineDeviceCount, baselineSetAt,
                lastSuccessfulFetchAt, latestFixTime, null);
    }

    public ProviderStatus recordError(Instant fetchAt, Throwable err) {
        return new ProviderStatus(state, degradedReason, degradedSince,
                consecutiveFailures + 1, 0, 0,
                lastDeviceCount, lastFreshCount, baselineDeviceCount, baselineSetAt,
                lastSuccessfulFetchAt, latestFixTime, err);
    }

    public ProviderStatus recordError(Instant fetchAt) {
        return recordError(fetchAt, null);
    }

    public ProviderStatus incrementClear() {
        return new ProviderStatus(state, degradedReason, degradedSince,
                consecutiveFailures, consecutiveEmpty, consecutiveClear + 1,
                lastDeviceCount, lastFreshCount, baselineDeviceCount, baselineSetAt,
                lastSuccessfulFetchAt, latestFixTime, lastError);
    }

    public ProviderStatus markDegraded(AlertKind reason, Instant since) {
        return new ProviderStatus(State.DEGRADED, reason, since,
                consecutiveFailures, consecutiveEmpty, 0,
                lastDeviceCount, lastFreshCount, baselineDeviceCount, baselineSetAt,
                lastSuccessfulFetchAt, latestFixTime, lastError);
    }

    public ProviderStatus markRecovered() {
        return new ProviderStatus(State.OK, null, null,
                0, 0, 0,
                lastDeviceCount, lastFreshCount, baselineDeviceCount, baselineSetAt,
                lastSuccessfulFetchAt, latestFixTime, null);
    }
}
