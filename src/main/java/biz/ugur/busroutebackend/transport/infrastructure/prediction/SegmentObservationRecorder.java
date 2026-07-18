package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.core.StopAware;
import biz.ugur.busroutebackend.prediction.shadow.V31Fix;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import biz.ugur.busroutebackend.prediction.shadow.V31StopEventSink;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentLiveStateRepository;
import biz.ugur.busroutebackend.transport.domain.repository.SegmentTravelStatsRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.SegmentTravelStat;
import biz.ugur.busroutebackend.transport.infrastructure.config.EtaLiveFactorProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class SegmentObservationRecorder implements V31StopEventSink {

    private static final Logger log = LoggerFactory.getLogger(SegmentObservationRecorder.class);
    private static final ZoneId ASHGABAT = ZoneId.of("Asia/Ashgabat");
    static final double MIN_TRAVEL_SECONDS = 10.0;
    static final double MAX_TRAVEL_SECONDS = 1800.0;
    static final double STOP_EDGE_OFFSET_METERS = 15.0;
    static final double MAX_ADVANCE_PER_TICK_METERS = 2000.0;
    static final double BACKWARD_RESET_METERS = 30.0;
    static final double SINGLE_TICK_SPAN_LIMIT_SECONDS = 45.0;
    private static final long SUMMARY_EVERY_TICKS = 20_000;

    private record TrackState(String routeNumber, int direction, long tripId,
                              double s, Instant at,
                              String pendingFromStopId, Instant pendingDepartAt) {
    }

    private final org.springframework.beans.factory.ObjectProvider<V31ShadowService> shadowService;
    private final SegmentTravelStatsRepository historyRepository;
    private final SegmentLiveStateRepository liveRepository;
    private final EtaLiveFactorProperties properties;

    private final Map<String, TrackState> tracks = new ConcurrentHashMap<>();
    private final AtomicLong ticksSeen = new AtomicLong();
    private final AtomicLong crossingsSeen = new AtomicLong();
    private final AtomicLong observationsWritten = new AtomicLong();
    private final AtomicLong droppedOutOfRange = new AtomicLong();
    private final AtomicLong resetsOnJump = new AtomicLong();

    public SegmentObservationRecorder(
            org.springframework.beans.factory.ObjectProvider<V31ShadowService> shadowService,
            SegmentTravelStatsRepository historyRepository,
            SegmentLiveStateRepository liveRepository,
            EtaLiveFactorProperties properties) {
        this.shadowService = shadowService;
        this.historyRepository = historyRepository;
        this.liveRepository = liveRepository;
        this.properties = properties;
    }

    @PostConstruct
    void register() {
        V31ShadowService shadow = shadowService.getIfAvailable();
        if (shadow == null) {
            log.info("[SEGMENT_OBS] v31 выключен — сбор сегментных наблюдений неактивен");
            return;
        }
        shadow.stopEventSink(this);
        log.info("[SEGMENT_OBS] детектор пересечений остановок подключён к v31-тикам");
    }

    @Override
    public void onTick(V31Fix fix, RouteLine leaderGeom, double s, int direction, long tripId,
                       List<StopAware.StopEvent> events) {
        if (!properties.isWriteEnabled()) {
            return;
        }
        if (properties.isAxisExcluded(fix.routeNumber(), direction)) {
            tracks.remove(fix.vehicleId());
            return;
        }
        long ticks = ticksSeen.incrementAndGet();
        if (ticks % SUMMARY_EVERY_TICKS == 0) {
            log.info("[SEGMENT_OBS] сводка: тиков={} пересечений={} наблюдений={} "
                            + "отброшено={} сбросов-скачков={}",
                    ticks, crossingsSeen.get(), observationsWritten.get(),
                    droppedOutOfRange.get(), resetsOnJump.get());
        }

        String vehicleId = fix.vehicleId();
        Instant now = fix.timestamp();
        TrackState prev = tracks.get(vehicleId);

        boolean sameRun = prev != null
                && prev.routeNumber().equals(fix.routeNumber())
                && prev.direction() == direction
                && prev.tripId() == tripId;
        if (!sameRun) {
            tracks.put(vehicleId, new TrackState(fix.routeNumber(), direction, tripId,
                    s, now, null, null));
            return;
        }

        double advance = s - prev.s();
        if (advance < -BACKWARD_RESET_METERS || advance > MAX_ADVANCE_PER_TICK_METERS) {
            resetsOnJump.incrementAndGet();
            tracks.put(vehicleId, new TrackState(fix.routeNumber(), direction, tripId,
                    s, now, null, null));
            return;
        }
        if (advance <= 0) {
            tracks.put(vehicleId, new TrackState(fix.routeNumber(), direction, tripId,
                    prev.s(), prev.at(), prev.pendingFromStopId(), prev.pendingDepartAt()));
            return;
        }

        String pendingFrom = prev.pendingFromStopId();
        Instant pendingDepartAt = prev.pendingDepartAt();
        double tickSpanSec = (now.toEpochMilli() - prev.at().toEpochMilli()) / 1000.0;

        for (RouteLine.StopPoint stop : leaderGeom.stops()) {
            double departEdge = stop.sMeters() + STOP_EDGE_OFFSET_METERS;
            double arriveEdge = stop.sMeters() - STOP_EDGE_OFFSET_METERS;

            if (arriveEdge > prev.s() && arriveEdge <= s && pendingFrom != null
                    && !pendingFrom.equals(stop.stopId())) {
                Instant arrivedAt = interpolate(prev.at(), tickSpanSec, prev.s(), s, arriveEdge);
                crossingsSeen.incrementAndGet();
                boolean departWithinSameTick = !pendingDepartAt.isBefore(prev.at());
                if (departWithinSameTick && tickSpanSec > SINGLE_TICK_SPAN_LIMIT_SECONDS) {
                    droppedOutOfRange.incrementAndGet();
                } else {
                    record(fix.routeNumber(), direction, pendingFrom, stop.stopId(),
                            pendingDepartAt, arrivedAt);
                }
                pendingFrom = null;
                pendingDepartAt = null;
            }
            if (departEdge > prev.s() && departEdge <= s) {
                pendingFrom = stop.stopId();
                pendingDepartAt = interpolate(prev.at(), tickSpanSec, prev.s(), s, departEdge);
                crossingsSeen.incrementAndGet();
            }
        }

        tracks.put(vehicleId, new TrackState(fix.routeNumber(), direction, tripId,
                s, now, pendingFrom, pendingDepartAt));
    }

    private static Instant interpolate(Instant fromAt, double spanSec,
                                       double fromS, double toS, double atS) {
        double f = toS - fromS <= 0 ? 0.0 : (atS - fromS) / (toS - fromS);
        return fromAt.plusMillis(Math.round(f * spanSec * 1000.0));
    }

    private void record(String routeNumber, int direction, String fromStopId, String toStopId,
                        Instant departedAt, Instant arrivedAt) {
        double elapsedSeconds =
                (arrivedAt.toEpochMilli() - departedAt.toEpochMilli()) / 1000.0;
        if (elapsedSeconds < MIN_TRAVEL_SECONDS || elapsedSeconds > MAX_TRAVEL_SECONDS) {
            droppedOutOfRange.incrementAndGet();
            return;
        }
        ZonedDateTime local = ZonedDateTime.ofInstant(arrivedAt, ASHGABAT);
        int hourOfDay = local.getHour();
        boolean weekend = local.getDayOfWeek().getValue() >= 6;

        Mono<SegmentTravelStat> history = historyRepository
                .findByKey(routeNumber, direction, fromStopId, toStopId, hourOfDay, weekend)
                .map(existing -> existing.withNewSample(elapsedSeconds, arrivedAt))
                .switchIfEmpty(Mono.defer(() -> Mono.just(
                        SegmentTravelStat.initial(routeNumber, direction, fromStopId, toStopId,
                                        hourOfDay, weekend)
                                .withNewSample(elapsedSeconds, arrivedAt))))
                .flatMap(historyRepository::save);

        history.then(liveRepository.recordTravel(fromStopId, toStopId, elapsedSeconds, arrivedAt))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.warn("[SEGMENT_OBS] запись наблюдения {} d{} {}->{} не удалась: {}",
                                routeNumber, direction, fromStopId, toStopId, err.getMessage()));
        observationsWritten.incrementAndGet();
        log.debug("[SEGMENT_OBS] {} d{} {}->{} elapsed={}s hour={} weekend={}",
                routeNumber, direction, fromStopId, toStopId,
                String.format(java.util.Locale.ROOT, "%.1f", elapsedSeconds), hourOfDay, weekend);
    }
}
