package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.routing.domain.model.raptor.StopTime;
import biz.ugur.busroutebackend.routing.domain.model.raptor.Trip;
import biz.ugur.busroutebackend.routing.domain.model.raptor.TripId;
import biz.ugur.busroutebackend.routing.domain.repository.StopTimeRepository;
import biz.ugur.busroutebackend.routing.domain.repository.TripRepository;
import biz.ugur.busroutebackend.transport.domain.enums.RouteDirection;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class RaptorTimetableGenerator {

    private static final int FALLBACK_DURATION_KMH = 5;

    private final DatabaseClient databaseClient;
    private final TripRepository tripRepository;
    private final StopTimeRepository stopTimeRepository;

    @Value("${routing.raptor.default-headway-seconds:600}")
    private int defaultHeadwaySeconds;

    @Value("${routing.raptor.service-day-start:06:00}")
    private String serviceDayStartRaw;

    @Value("${routing.raptor.service-day-end:23:00}")
    private String serviceDayEndRaw;

    @Value("${routing.raptor.service-id:ALL_DAYS}")
    private String serviceId;

    public RaptorTimetableGenerator(DatabaseClient databaseClient,
                                     TripRepository tripRepository,
                                     StopTimeRepository stopTimeRepository) {
        this.databaseClient = databaseClient;
        this.tripRepository = tripRepository;
        this.stopTimeRepository = stopTimeRepository;
    }

    public Mono<GenerationResult> regenerate() {
        LocalTime serviceDayStart = LocalTime.parse(serviceDayStartRaw);
        LocalTime serviceDayEnd = LocalTime.parse(serviceDayEndRaw);

        return stopTimeRepository.deleteAll()
                .then(tripRepository.deleteAll())
                .thenMany(loadRouteDirections())
                .collectList()
                .flatMap(rows -> generateFromRows(rows, serviceDayStart, serviceDayEnd));
    }

    private Flux<RouteDirectionRow> loadRouteDirections() {
        String sql = """
                SELECT
                    br.id                                  AS route_id,
                    br.route_number                        AS route_number,
                    br.estimated_duration_minutes          AS duration_min,
                    COALESCE(br.total_distance_forward_meters, br.total_distance_backward_meters) AS distance_m,
                    rs.direction                           AS direction,
                    rs.stop_sequence                       AS stop_sequence,
                    rs.stop_id                             AS stop_id
                FROM bus_routes br
                JOIN route_stops rs ON rs.route_id = br.id
                WHERE br.is_active = true
                ORDER BY br.id, rs.direction, rs.stop_sequence
                """;
        return databaseClient.sql(sql)
                .map((row, meta) -> new RouteDirectionRow(
                        row.get("route_id", String.class),
                        row.get("route_number", String.class),
                        row.get("duration_min", Integer.class),
                        row.get("distance_m", Integer.class),
                        row.get("direction", Integer.class),
                        row.get("stop_sequence", Integer.class),
                        row.get("stop_id", String.class)
                ))
                .all();
    }

    private Mono<GenerationResult> generateFromRows(List<RouteDirectionRow> rows,
                                                     LocalTime serviceDayStart,
                                                     LocalTime serviceDayEnd) {
        if (rows.isEmpty()) {
            log.warn("[RAPTOR_GEN] No active bus_routes joined with route_stops — nothing to generate");
            return Mono.just(new GenerationResult(0, 0, 0));
        }

        List<Trip> tripsToInsert = new ArrayList<>();
        List<StopTime> stopTimesToInsert = new ArrayList<>();
        int skipped = 0;

        String currentRouteId = null;
        Integer currentDirection = null;
        List<RouteDirectionRow> currentGroup = new ArrayList<>();

        for (RouteDirectionRow row : rows) {
            boolean groupBoundary = !row.routeId().equals(currentRouteId)
                    || !row.direction().equals(currentDirection);
            if (groupBoundary && !currentGroup.isEmpty()) {
                if (!appendGroup(currentGroup, serviceDayStart, serviceDayEnd, tripsToInsert, stopTimesToInsert)) {
                    skipped++;
                }
                currentGroup.clear();
            }
            currentRouteId = row.routeId();
            currentDirection = row.direction();
            currentGroup.add(row);
        }
        if (!currentGroup.isEmpty()) {
            if (!appendGroup(currentGroup, serviceDayStart, serviceDayEnd, tripsToInsert, stopTimesToInsert)) {
                skipped++;
            }
        }

        log.info("[RAPTOR_GEN] generated trips={} stop_times={} skipped_groups={}",
                tripsToInsert.size(), stopTimesToInsert.size(), skipped);

        final int skippedFinal = skipped;
        return tripRepository.saveAll(tripsToInsert)
                .then(stopTimeRepository.saveAll(stopTimesToInsert).then())
                .thenReturn(new GenerationResult(tripsToInsert.size(), stopTimesToInsert.size(), skippedFinal));
    }

    private boolean appendGroup(List<RouteDirectionRow> group,
                                 LocalTime serviceDayStart,
                                 LocalTime serviceDayEnd,
                                 List<Trip> tripsOut,
                                 List<StopTime> stopTimesOut) {
        RouteDirectionRow head = group.get(0);
        int nStops = group.size();
        int nHops = nStops - 1;
        if (nHops < 1) {
            log.warn("[RAPTOR_GEN] skip route={} dir={} — only {} stop(s), need >= 2",
                    head.routeNumber(), head.direction(), nStops);
            return false;
        }

        int totalSeconds = resolveTotalSeconds(head);
        if (totalSeconds <= 0) {
            log.warn("[RAPTOR_GEN] skip route={} dir={} — cannot resolve total trip seconds (duration_min={}, distance_m={})",
                    head.routeNumber(), head.direction(), head.durationMin(), head.distanceM());
            return false;
        }

        double secondsPerHop = (double) totalSeconds / nHops;

        Trip trip = Trip.frequencyBased(
                BusRouteId.of(head.routeId()),
                RouteDirection.fromValue(head.direction()),
                serviceId,
                defaultHeadwaySeconds,
                serviceDayStart,
                serviceDayEnd);
        tripsOut.add(trip);

        for (int i = 0; i < group.size(); i++) {
            RouteDirectionRow r = group.get(i);
            int offsetSec = (int) Math.round(i * secondsPerHop);
            int raptorSequence = i + 1;
            if (!r.stopSequence().equals(raptorSequence)) {
                log.debug("[RAPTOR_GEN] route={} dir={} renumber raw_seq={} -> {} (stop={})",
                        head.routeNumber(), head.direction(),
                        r.stopSequence(), raptorSequence, r.stopId());
            }
            stopTimesOut.add(StopTime.instantTransit(
                    trip.getId(),
                    raptorSequence,
                    BusStopId.of(r.stopId()),
                    offsetSec));
        }
        return true;
    }

    private int resolveTotalSeconds(RouteDirectionRow head) {
        if (head.durationMin() != null && head.durationMin() > 0) {
            return head.durationMin() * 60;
        }
        if (head.distanceM() != null && head.distanceM() > 0) {
            return (int) Math.round(head.distanceM() / 1000.0 / FALLBACK_DURATION_KMH * 3600);
        }
        return 0;
    }

    public record GenerationResult(int tripsCreated, int stopTimesCreated, int skippedGroups) {
    }

    private record RouteDirectionRow(String routeId,
                                      String routeNumber,
                                      Integer durationMin,
                                      Integer distanceM,
                                      Integer direction,
                                      Integer stopSequence,
                                      String stopId) {
    }
}
