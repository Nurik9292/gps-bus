package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import biz.ugur.busroutebackend.shared.infrastructure.email.AlertQuietHours;
import biz.ugur.busroutebackend.shared.infrastructure.email.EmailNotificationService;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.AlertKind;
import biz.ugur.busroutebackend.transport.domain.enums.ShiftType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class RouteSwapMonitor {

    private static final double FIRST_FIX_CREDIT_MINUTES = 0.5;
    private static final double MAX_FIX_GAP_CREDIT_SECONDS = 90.0;
    private static final Duration DIGEST_SEND_TIMEOUT = Duration.ofSeconds(30);

    private final RouteSwapProperties properties;
    private final RouteSwapGeoDictionary dictionary;
    private final EmailNotificationService emailService;
    private final AlertQuietHours quietHours;
    private final Map<String, VehicleState> vehicleStates = new ConcurrentHashMap<>();
    private final Map<EpisodeKey, Boolean> alertedEpisodes = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> digestBuffer = new ConcurrentLinkedQueue<>();
    private final AtomicLong skippedNoDictionary = new AtomicLong();
    private volatile Disposable tapSubscription;

    public RouteSwapMonitor(RouteSwapProperties properties,
                            RouteSwapGeoDictionary dictionary,
                            EmailNotificationService emailService,
                            AlertQuietHours quietHours) {
        this.properties = properties;
        this.dictionary = dictionary;
        this.emailService = emailService;
        this.quietHours = quietHours;
    }

    public void attachTo(RouteSwapTap tap) {
        tapSubscription = tap.flux()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(
                        fix -> {
                            try {
                                onFix(fix);
                            } catch (RuntimeException e) {
                                tap.recordError();
                                log.warn("[ROUTE_SWAP] fix processing failed for {}: {}",
                                        fix.vehicleId(), e.getMessage());
                            }
                        },
                        error -> log.error("[ROUTE_SWAP] tap stream failed", error));
    }

    public void shutdown() {
        Disposable subscription = tapSubscription;
        if (subscription != null) {
            subscription.dispose();
        }
    }

    public void onFix(RouteSwapFix fix) {
        if (properties.getMode() == RouteSwapProperties.Mode.OFF) {
            return;
        }
        if (fix.routeId() == null || fix.routeId().isBlank()) {
            return;
        }
        if (fix.inGarage()) {
            vehicleStates.remove(fix.vehicleId());
            return;
        }
        RouteSwapGeoDictionary.Snapshot snapshot = dictionary.current();
        if (snapshot == null) {
            skippedNoDictionary.incrementAndGet();
            dictionary.ensureBuilt();
            return;
        }
        if (!snapshot.knowsAxis(fix.routeId())) {
            return;
        }
        VehicleState state = vehicleStates.computeIfAbsent(fix.vehicleId(), id -> new VehicleState());
        synchronized (state) {
            processFix(state, fix, snapshot);
        }
    }

    private void processFix(VehicleState state, RouteSwapFix fix, RouteSwapGeoDictionary.Snapshot snapshot) {
        Instant lastFix = state.lastFixAt;
        if (lastFix != null && !fix.timestamp().isAfter(lastFix)) {
            return;
        }
        if (state.routeId == null) {
            state.graceUntil = fix.timestamp().plusSeconds(properties.getAssignmentGraceMinutes() * 60L);
        } else if (!state.routeId.equals(fix.routeId())) {
            state.resetWindows();
            state.graceUntil = fix.timestamp().plusSeconds(properties.getAssignmentGraceMinutes() * 60L);
        }
        state.routeId = fix.routeId();
        state.routeNumber = fix.routeNumber();
        state.licensePlate = fix.licensePlate();

        long bucket = fix.timestamp().getEpochSecond() / 60 / properties.getWindowMinutes();
        if (state.currentBucket != bucket) {
            finalizeWindow(state, snapshot);
            state.currentBucket = bucket;
        }
        if (fix.speedKmh() < properties.getMinSpeedKmh()) {
            return;
        }
        double familyDistance = snapshot.familyDistanceMeters(fix.routeId(), fix.latitude(), fix.longitude());
        double axisDistance = snapshot.axisDistanceMeters(fix.routeId(), fix.latitude(), fix.longitude());
        WindowSample sample = new WindowSample(fix.timestamp(), familyDistance, axisDistance, null, false);
        if (familyDistance > properties.getOffAssignedMeters()) {
            Optional<RouteSwapGeoDictionary.ForeignMatch> foreign =
                    snapshot.bestForeign(fix.routeId(), fix.latitude(), fix.longitude());
            if (foreign.isPresent() && foreign.get().distanceMeters() <= properties.getOnRouteMeters()) {
                sample = new WindowSample(fix.timestamp(), familyDistance, axisDistance,
                        foreign.get().familyKey(), foreign.get().uniqueVisit());
            }
        }
        state.windowSamples.add(sample);
        state.lastFixAt = fix.timestamp();
    }

    private void finalizeWindow(VehicleState state, RouteSwapGeoDictionary.Snapshot snapshot) {
        List<WindowSample> samples = state.windowSamples;
        if (state.currentBucket != Long.MIN_VALUE
                && samples.size() >= properties.getMinMovingPointsPerWindow()) {
            state.completedWindows.addLast(WindowVerdict.of(samples));
            while (state.completedWindows.size() > properties.getVerdictWindows()) {
                state.completedWindows.removeFirst();
            }
            evaluate(state, snapshot);
        }
        state.windowSamples = new ArrayList<>();
    }

    private void evaluate(VehicleState state, RouteSwapGeoDictionary.Snapshot snapshot) {
        Deque<WindowVerdict> windows = state.completedWindows;
        if (windows.size() < properties.getMinWindowsForVerdict()) {
            return;
        }
        Instant lastFix = state.lastFixAt;
        if (lastFix == null || (state.graceUntil != null && lastFix.isBefore(state.graceUntil))) {
            return;
        }
        int total = windows.size();
        long assignedOn = windows.stream().filter(w -> w.medianFamilyMeters <= properties.getOnRouteMeters()).count();
        long axisOn = windows.stream().filter(w -> w.medianAxisMeters <= properties.getOnRouteMeters()).count();
        double assignedCoverage = (double) assignedOn / total;
        double axisCoverage = (double) axisOn / total;

        Map<String, Long> foreignWindows = new HashMap<>();
        for (WindowVerdict window : windows) {
            if (window.foreignTopKey != null
                    && window.foreignTopOnCount >= properties.getMinMovingPointsPerWindow()) {
                foreignWindows.merge(window.foreignTopKey, 1L, Long::sum);
            }
        }
        Optional<Map.Entry<String, Long>> bestForeign = foreignWindows.entrySet().stream()
                .max(Comparator.comparingLong(Map.Entry::getValue));
        double foreignCoverage = bestForeign.map(e -> (double) e.getValue() / total).orElse(0.0);
        double uniqueMinutes = bestForeign
                .map(e -> windows.stream()
                        .filter(w -> e.getKey().equals(w.foreignTopKey))
                        .mapToDouble(w -> w.foreignUniqueMinutes)
                        .sum())
                .orElse(0.0);

        if (assignedCoverage <= properties.getSwapAssignedCoverageMax()
                && foreignCoverage >= properties.getSwapForeignCoverageMin()
                && uniqueMinutes >= properties.getUniqueMinutesMin()) {
            emit(state, lastFix, "SWAP_SUSPECTED",
                    String.format("factual=%s foreignCov=%.0f%% uniqueMin=%.0f",
                            snapshot.familyLabel(bestForeign.orElseThrow().getKey()),
                            foreignCoverage * 100, uniqueMinutes));
            return;
        }
        if (assignedCoverage <= properties.getSwapAssignedCoverageMax()
                && foreignCoverage < properties.getSwapForeignCoverageMin()) {
            emit(state, lastFix, "NO_AXIS_FITS",
                    String.format("asgCov=%.0f%% bestForeignCov=%.0f%%",
                            assignedCoverage * 100, foreignCoverage * 100));
            return;
        }
        if (assignedCoverage >= properties.getIntraFamilyCoverageMin()
                && axisCoverage <= properties.getSwapAssignedCoverageMax()) {
            emit(state, lastFix, "INTRA_FAMILY_MISMATCH",
                    String.format("familyCov=%.0f%% assignedAxisCov=%.0f%%",
                            assignedCoverage * 100, axisCoverage * 100));
        }
    }

    private void emit(VehicleState state, Instant at, String verdict, String detail) {
        LocalDate operationalDate = ShiftType.operationalDateAt(at);
        String shift = ShiftType.operationalShiftAt(at).map(Enum::name).orElse("OUTSIDE");
        EpisodeKey episodeKey = new EpisodeKey(state.licensePlate, operationalDate, shift, verdict);
        if (alertedEpisodes.putIfAbsent(episodeKey, Boolean.TRUE) != null) {
            return;
        }
        String line = String.format("%s | assigned r%s | %s | %s | windows=%d | %s %s",
                state.licensePlate, state.routeNumber, verdict, detail,
                state.completedWindows.size(), operationalDate, shift);
        digestBuffer.add(line);
        log.warn("[ROUTE_SWAP] {}", line);
    }

    @Scheduled(fixedRateString = "${business.route-swap-detector.digest-interval-minutes:30}",
            timeUnit = TimeUnit.MINUTES)
    public void flushDigest() {
        if (properties.getMode() == RouteSwapProperties.Mode.OFF) {
            digestBuffer.clear();
            return;
        }
        if (quietHours.active() || digestBuffer.isEmpty()) {
            return;
        }
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = digestBuffer.poll()) != null) {
            lines.add(line);
        }
        String subject = "[ROUTE_SWAP] " + lines.size() + " episode(s), mode=" + properties.getMode();
        String body = "<pre>" + String.join("\n", lines) + "</pre>";
        emailService.sendGpsAlert(properties.recipientList(), "route-swap-digest",
                        AlertKind.ROUTE_SWAP, subject, body)
                .timeout(DIGEST_SEND_TIMEOUT)
                .doOnError(error -> log.error("[ROUTE_SWAP] digest send failed", error))
                .onErrorResume(error -> reactor.core.publisher.Mono.empty())
                .subscribe();
    }

    @Scheduled(cron = "0 20 0 * * *", zone = "UTC")
    public void cleanupOldEpisodes() {
        LocalDate cutoff = ShiftType.operationalDateAt(Instant.now()).minusDays(1);
        alertedEpisodes.keySet().removeIf(key -> key.operationalDate().isBefore(cutoff));
        Instant staleBefore = Instant.now().minusSeconds(24 * 3600L);
        vehicleStates.entrySet().removeIf(entry -> {
            Instant lastFix = entry.getValue().lastFixAt;
            return lastFix == null || lastFix.isBefore(staleBefore);
        });
    }

    public long skippedNoDictionaryCount() {
        return skippedNoDictionary.get();
    }

    private record EpisodeKey(String licensePlate, LocalDate operationalDate, String shift, String verdict) {
    }

    private record WindowSample(Instant timestamp, double familyMeters, double axisMeters,
                                String foreignFamilyKey, boolean uniqueVisit) {
    }

    private static final class WindowVerdict {
        private final double medianFamilyMeters;
        private final double medianAxisMeters;
        private final String foreignTopKey;
        private final int foreignTopOnCount;
        private final double foreignUniqueMinutes;

        private WindowVerdict(double medianFamilyMeters, double medianAxisMeters,
                              String foreignTopKey, int foreignTopOnCount, double foreignUniqueMinutes) {
            this.medianFamilyMeters = medianFamilyMeters;
            this.medianAxisMeters = medianAxisMeters;
            this.foreignTopKey = foreignTopKey;
            this.foreignTopOnCount = foreignTopOnCount;
            this.foreignUniqueMinutes = foreignUniqueMinutes;
        }

        static WindowVerdict of(List<WindowSample> samples) {
            Map<String, Integer> foreignCounts = new HashMap<>();
            for (WindowSample sample : samples) {
                if (sample.foreignFamilyKey() != null) {
                    foreignCounts.merge(sample.foreignFamilyKey(), 1, Integer::sum);
                }
            }
            Optional<Map.Entry<String, Integer>> top = foreignCounts.entrySet().stream()
                    .max(Comparator.comparingInt(Map.Entry::getValue));
            String topKey = top.map(Map.Entry::getKey).orElse(null);
            return new WindowVerdict(
                    median(samples.stream().mapToDouble(WindowSample::familyMeters).sorted().toArray()),
                    median(samples.stream().mapToDouble(WindowSample::axisMeters).sorted().toArray()),
                    topKey,
                    top.map(Map.Entry::getValue).orElse(0),
                    uniqueMinutes(samples, topKey));
        }

        private static double uniqueMinutes(List<WindowSample> samples, String topKey) {
            if (topKey == null) {
                return 0.0;
            }
            double minutes = 0.0;
            Instant previous = null;
            for (WindowSample sample : samples) {
                if (topKey.equals(sample.foreignFamilyKey()) && sample.uniqueVisit()) {
                    if (previous == null) {
                        minutes += FIRST_FIX_CREDIT_MINUTES;
                    } else {
                        double gapSeconds = Duration.between(previous, sample.timestamp()).toMillis() / 1000.0;
                        minutes += Math.clamp(gapSeconds, 0.0, MAX_FIX_GAP_CREDIT_SECONDS) / 60.0;
                    }
                }
                previous = sample.timestamp();
            }
            return minutes;
        }

        private static double median(double[] sorted) {
            if (sorted.length == 0) {
                return Double.POSITIVE_INFINITY;
            }
            return sorted[sorted.length / 2];
        }
    }

    private static final class VehicleState {
        private String routeId;
        private String routeNumber;
        private String licensePlate;
        private Instant graceUntil;
        private volatile Instant lastFixAt;
        private long currentBucket = Long.MIN_VALUE;
        private List<WindowSample> windowSamples = new ArrayList<>();
        private final Deque<WindowVerdict> completedWindows = new ArrayDeque<>();

        private void resetWindows() {
            windowSamples = new ArrayList<>();
            completedWindows.clear();
            currentBucket = Long.MIN_VALUE;
        }
    }
}
