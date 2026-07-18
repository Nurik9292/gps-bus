package biz.ugur.busroutebackend.transport.infrastructure.prediction;

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

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SegmentObservationRecorder implements V31StopEventSink {

    private static final Logger log = LoggerFactory.getLogger(SegmentObservationRecorder.class);
    private static final ZoneId ASHGABAT = ZoneId.of("Asia/Ashgabat");
    static final double MIN_TRAVEL_SECONDS = 10.0;
    static final double MAX_TRAVEL_SECONDS = 1800.0;
    private static final Duration STATE_EXPIRY = Duration.ofMinutes(30);

    private record DepartureState(String routeNumber, int direction, long tripId,
                                  String stopId, Instant at) {
    }

    private final V31ShadowService shadowService;
    private final SegmentTravelStatsRepository historyRepository;
    private final SegmentLiveStateRepository liveRepository;
    private final EtaLiveFactorProperties properties;

    private final Map<String, DepartureState> departures = new ConcurrentHashMap<>();

    public SegmentObservationRecorder(V31ShadowService shadowService,
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
        shadowService.stopEventSink(this);
        log.info("[SEGMENT_OBS] писатель сегментных наблюдений подключён к v31-стоп-событиям");
    }

    @Override
    public void accept(V31Fix fix, int direction, long tripId, List<StopAware.StopEvent> events) {
        if (!properties.isWriteEnabled()) {
            return;
        }
        if (properties.isAxisExcluded(fix.routeNumber(), direction)) {
            departures.remove(fix.vehicleId());
            return;
        }
        for (StopAware.StopEvent event : events) {
            handle(fix, direction, tripId, event);
        }
    }

    private void handle(V31Fix fix, int direction, long tripId, StopAware.StopEvent event) {
        String vehicleId = fix.vehicleId();
        switch (event.type()) {
            case DWELL_ENTER -> {
                arriveAt(fix, direction, tripId, event);
                departures.remove(vehicleId);
            }
            case SKIP -> {
                arriveAt(fix, direction, tripId, event);
                departures.put(vehicleId, new DepartureState(
                        fix.routeNumber(), direction, tripId, event.stopId(), event.at()));
            }
            case DWELL_EXIT -> departures.put(vehicleId, new DepartureState(
                    fix.routeNumber(), direction, tripId, event.stopId(), event.at()));
            case AT_TERMINAL -> departures.remove(vehicleId);
            case DECEL_ENTER, DWELL_OUTLIER -> {
            }
        }
    }

    private void arriveAt(V31Fix fix, int direction, long tripId, StopAware.StopEvent arrival) {
        DepartureState departure = departures.get(fix.vehicleId());
        if (departure == null
                || !departure.routeNumber().equals(fix.routeNumber())
                || departure.direction() != direction
                || departure.tripId() != tripId
                || departure.stopId().equals(arrival.stopId())) {
            return;
        }
        if (Duration.between(departure.at(), arrival.at()).compareTo(STATE_EXPIRY) > 0) {
            return;
        }
        double elapsedSeconds =
                (arrival.at().toEpochMilli() - departure.at().toEpochMilli()) / 1000.0;
        if (elapsedSeconds < MIN_TRAVEL_SECONDS || elapsedSeconds > MAX_TRAVEL_SECONDS) {
            return;
        }
        persist(fix.routeNumber(), direction, departure.stopId(), arrival.stopId(),
                elapsedSeconds, arrival.at());
    }

    private void persist(String routeNumber, int direction, String fromStopId, String toStopId,
                         double elapsedSeconds, Instant observedAt) {
        ZonedDateTime local = ZonedDateTime.ofInstant(observedAt, ASHGABAT);
        int hourOfDay = local.getHour();
        boolean weekend = local.getDayOfWeek().getValue() >= 6;

        Mono<SegmentTravelStat> history = historyRepository
                .findByKey(routeNumber, direction, fromStopId, toStopId, hourOfDay, weekend)
                .map(existing -> existing.withNewSample(elapsedSeconds, observedAt))
                .switchIfEmpty(Mono.defer(() -> Mono.just(
                        SegmentTravelStat.initial(routeNumber, direction, fromStopId, toStopId,
                                        hourOfDay, weekend)
                                .withNewSample(elapsedSeconds, observedAt))))
                .flatMap(historyRepository::save);

        history.then(liveRepository.recordTravel(fromStopId, toStopId, elapsedSeconds, observedAt))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        err -> log.warn("[SEGMENT_OBS] запись наблюдения {} d{} {}->{} не удалась: {}",
                                routeNumber, direction, fromStopId, toStopId, err.getMessage()));
        log.debug("[SEGMENT_OBS] {} d{} {}->{} elapsed={}s hour={} weekend={}",
                routeNumber, direction, fromStopId, toStopId,
                String.format(java.util.Locale.ROOT, "%.1f", elapsedSeconds), hourOfDay, weekend);
    }
}
