package biz.ugur.busroutebackend.transport.domain.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class FrozenCoordsRegistry {

    public record FrozenEpisode(String deviceId, String licensePlate, String routeNumber,
                                Instant firstFrozenAt, Instant lastFrozenAt, long detectionCount,
                                Double lastReportedSpeedKmh, boolean warnAllowed) {
        private FrozenEpisode withWarnAllowed(boolean allowed) {
            return new FrozenEpisode(deviceId, licensePlate, routeNumber,
                    firstFrozenAt, lastFrozenAt, detectionCount, lastReportedSpeedKmh, allowed);
        }
    }

    private final Duration warnDedupInterval;
    private final Duration chronicThreshold;
    private final Duration episodeRetention;
    private final Clock clock;
    private final ConcurrentHashMap<String, FrozenEpisode> episodesByDevice = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> warnSlotsByDevice = new ConcurrentHashMap<>();
    private final AtomicReference<Instant> lastPruneAt = new AtomicReference<>(Instant.EPOCH);

    public FrozenCoordsRegistry(Duration warnDedupInterval, Duration chronicThreshold,
                                Duration episodeRetention, Clock clock) {
        this.warnDedupInterval = warnDedupInterval;
        this.chronicThreshold = chronicThreshold;
        this.episodeRetention = episodeRetention;
        this.clock = clock;
    }

    public FrozenEpisode recordFrozenEvent(String deviceId, String licensePlate,
                                           String routeNumber, Double reportedSpeedKmh) {
        Instant now = clock.instant();
        pruneIfDue(now);
        FrozenEpisode updated = episodesByDevice.compute(deviceId, (id, current) ->
                current == null || isEpisodeStale(current, now)
                        ? new FrozenEpisode(id, licensePlate, routeNumber, now, now, 1,
                                reportedSpeedKmh, false)
                        : new FrozenEpisode(id,
                                licensePlate != null ? licensePlate : current.licensePlate(),
                                routeNumber != null ? routeNumber : current.routeNumber(),
                                current.firstFrozenAt(), now, current.detectionCount() + 1,
                                reportedSpeedKmh != null ? reportedSpeedKmh
                                        : current.lastReportedSpeedKmh(),
                                false));
        return updated.withWarnAllowed(tryAcquireWarnSlot(deviceId, now));
    }

    public void recordCoordinatesMoved(String deviceId) {
        episodesByDevice.remove(deviceId);
    }

    public List<FrozenEpisode> chronicallyFrozen() {
        Instant now = clock.instant();
        pruneIfDue(now);
        return episodesByDevice.values().stream()
                .filter(episode -> !isEpisodeStale(episode, now))
                .filter(episode -> Duration.between(episode.firstFrozenAt(), now)
                        .compareTo(chronicThreshold) >= 0)
                .sorted(Comparator.comparing(FrozenEpisode::firstFrozenAt))
                .toList();
    }

    private boolean tryAcquireWarnSlot(String deviceId, Instant now) {
        Instant granted = warnSlotsByDevice.compute(deviceId, (id, lastWarnAt) ->
                lastWarnAt == null
                        || Duration.between(lastWarnAt, now).compareTo(warnDedupInterval) >= 0
                        ? now : lastWarnAt);
        return granted.equals(now);
    }

    private boolean isEpisodeStale(FrozenEpisode episode, Instant now) {
        return Duration.between(episode.lastFrozenAt(), now).compareTo(episodeRetention) > 0;
    }

    private void pruneIfDue(Instant now) {
        Instant lastPrune = lastPruneAt.get();
        if (Duration.between(lastPrune, now).compareTo(episodeRetention) < 0
                || !lastPruneAt.compareAndSet(lastPrune, now)) {
            return;
        }
        episodesByDevice.values().removeIf(episode -> isEpisodeStale(episode, now));
        warnSlotsByDevice.values().removeIf(lastWarnAt ->
                Duration.between(lastWarnAt, now).compareTo(episodeRetention) > 0);
    }
}
