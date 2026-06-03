package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.shared.domain.event.DomainEventPublisher;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.event.VehiclePositionUpdatedEvent;
import biz.ugur.busroutebackend.transport.domain.model.Vehicle;
import biz.ugur.busroutebackend.transport.domain.repository.VehicleRepository;
import biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.GatekeeperDecision;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePositionPredictionService;
import biz.ugur.busroutebackend.transport.infrastructure.redis.VehicleGpsHistoryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PersistAndBroadcastStage {

    private static final long FORCE_PUBLISH_INTERVAL_SECONDS = 20;

    private final ConcurrentHashMap<String, Instant> lastPublishedTime = new ConcurrentHashMap<>();

    private final VehiclePositionPredictionService predictionService;
    private final VehicleRepository vehicleRepository;
    private final VehicleGpsHistoryService gpsHistoryService;
    private final DomainEventPublisher domainEventPublisher;
    private final PipelineTracer pipelineTracer;

    public PersistAndBroadcastStage(VehiclePositionPredictionService predictionService,
                                     VehicleRepository vehicleRepository,
                                     VehicleGpsHistoryService gpsHistoryService,
                                     DomainEventPublisher domainEventPublisher,
                                     PipelineTracer pipelineTracer) {
        this.predictionService = predictionService;
        this.vehicleRepository = vehicleRepository;
        this.gpsHistoryService = gpsHistoryService;
        this.domainEventPublisher = domainEventPublisher;
        this.pipelineTracer = pipelineTracer;
    }

    public record Context(
            List<Vehicle> updatedVehicles,
            List<Vehicle> vehiclesToCreate,
            Map<String, Double> estimatedBearings,
            Map<String, double[]> oldCoordsByVehicleId,
            Map<String, Boolean> hasSignificantChangeById,
            Map<String, Boolean> frozenCoordsWithMotionById,
            Set<String> frozenCoordsDeviceIds,
            Map<String, Boolean> bufferedByDeviceId,
            Map<String, GpsPositionDTO> latestPositionsByDevice) {}

    public Mono<Void> apply(Context ctx) {
        Mono<Void> predictionsMono = dispatchPredictionsOffEventLoop(ctx);

        return predictionsMono.then(Mono.defer(() -> {
            Map<String, Integer> directionFixes = predictionService.drainPendingDirectionFixes();

            List<Vehicle> gatedVehicles = ctx.updatedVehicles().stream()
                    .map(v -> applyGatekeeperDecision(v, ctx.oldCoordsByVehicleId()))
                    .map(v -> applyPendingDirectionFix(v, directionFixes))
                    .toList();

            for (Vehicle v : gatedVehicles) {
                publishPositionEvent(v,
                        ctx.hasSignificantChangeById().getOrDefault(v.getId().getValue(), false),
                        ctx.frozenCoordsWithMotionById().getOrDefault(v.getId().getValue(), false));
            }

            Set<String> fixedInBatch = gatedVehicles.stream()
                    .map(v -> v.getId().getValue())
                    .filter(directionFixes::containsKey)
                    .collect(java.util.stream.Collectors.toSet());
            Map<String, Integer> leftoverFixes = directionFixes.entrySet().stream()
                    .filter(e -> !fixedInBatch.contains(e.getKey()))
                    .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Mono<Integer> dirFixMono = leftoverFixes.isEmpty()
                    ? Mono.just(0)
                    : vehicleRepository.batchUpdateDirections(leftoverFixes)
                            .doOnNext(n -> log.debug("[GPS_PIPELINE] DIR_AUTO_FIX applied {} direction corrections (leftover, vehicle not in batch)", n));

            Mono<Integer> updateMono = gatedVehicles.isEmpty()
                    ? Mono.just(0)
                    : vehicleRepository.batchUpdate(gatedVehicles);

            Mono<List<Vehicle>> insertMono = ctx.vehiclesToCreate().isEmpty()
                    ? Mono.just(List.of())
                    : vehicleRepository.batchInsert(ctx.vehiclesToCreate()).collectList();

            return updateMono
                    .then(Mono.zip(insertMono, dirFixMono))
                    .flatMap(tuple -> persistRawGpsHistory(ctx).thenReturn(tuple))
                    .doOnNext(tuple ->
                            log.debug("Batch operations: updated={} created={} dir-fixes-leftover={} dir-fixes-merged-into-batch={}",
                                    gatedVehicles.size(), tuple.getT1().size(), tuple.getT2(), fixedInBatch.size()))
                    .then();
        }));
    }

    private Vehicle applyPendingDirectionFix(Vehicle vehicle, Map<String, Integer> directionFixes) {
        if (directionFixes.isEmpty()) {
            return vehicle;
        }
        Integer fixedDirection = directionFixes.get(vehicle.getId().getValue());
        if (fixedDirection == null) {
            return vehicle;
        }
        if (vehicle.getCurrentDirection() != null && vehicle.getCurrentDirection().equals(fixedDirection)) {
            return vehicle;
        }
        log.info("[GPS_PIPELINE] DIR_AUTO_FIX_MERGED_INTO_BATCH vehicle={} plate={} batchDir={} → fixedDir={} (snap-corrected direction merged into Vehicle before batchUpdate to avoid race with batchUpdateDirections)",
                vehicle.getId().getValue(), vehicle.getLicensePlate(),
                vehicle.getCurrentDirection(), fixedDirection);
        return vehicle.toBuilder()
                .currentDirection(fixedDirection)
                .build();
    }

    private Mono<Void> dispatchPredictionsOffEventLoop(Context ctx) {
        return Mono.<Void>fromRunnable(() -> runPredictionLoop(ctx))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void runPredictionLoop(Context ctx) {
        long loopT0 = System.nanoTime();
        int dispatchedCount = 0;
        long maxCallMicros = 0;

        for (Vehicle v : ctx.updatedVehicles()) {
            if (v.getCurrentLatitude() == null || v.getCurrentLongitude() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(ctx.frozenCoordsWithMotionById().get(v.getId().getValue()))) {
                log.debug("[GPS_PIPELINE] FROZEN_FIX_SKIP_PREDICTION vehicle={} plate={} — prediction advance + history suppressed",
                        v.getId().getValue(), v.getLicensePlate());
                continue;
            }

            long callT0 = System.nanoTime();
            predictionService.onGpsUpdate(
                    v.getId().getValue(),
                    v.getLicensePlate(),
                    v.getRouteNumber(),
                    v.getCurrentLatitude(),
                    v.getCurrentLongitude(),
                    v.getSpeedKmh() != null ? v.getSpeedKmh() : 0.0,
                    (v.getCourse() != null && v.getCourse() > 0.0)
                            ? v.getCourse()
                            : ctx.estimatedBearings().getOrDefault(v.getId().getValue(), 0.0),
                    Boolean.TRUE.equals(v.getIsInMotion()),
                    v.getLastPositionUpdate() != null
                            ? v.getLastPositionUpdate().toInstant(ZoneOffset.UTC)
                            : Instant.now(),
                    v.getCurrentDirection() != null ? v.getCurrentDirection() : 0,
                    v.getCurrentDirection() != null,
                    Boolean.TRUE.equals(v.getIsInGarage()),
                    Boolean.TRUE.equals(ctx.bufferedByDeviceId().get(v.getDeviceId()))
            );
            long callMicros = (System.nanoTime() - callT0) / 1000;
            dispatchedCount++;
            if (callMicros > maxCallMicros) maxCallMicros = callMicros;
            if (callMicros > 5_000) {
                log.warn("[GPS_PIPELINE] ON_GPS_UPDATE_SLOW vehicle={} plate={} route={} durationMicros={} — single call exceeded 5ms blocking budget on event-loop",
                        v.getId().getValue(), v.getLicensePlate(), v.getRouteNumber(), callMicros);
            } else {
                log.debug("[GPS_PIPELINE] ON_GPS_UPDATE_TIMING vehicle={} durationMicros={}",
                        v.getId().getValue(), callMicros);
            }
        }

        long loopMicros = (System.nanoTime() - loopT0) / 1000;
        if (loopMicros > 50_000) {
            log.warn("[GPS_PIPELINE] ON_GPS_UPDATE_LOOP_SLOW vehicles={} dispatched={} totalMicros={} maxCallMicros={} — for-loop on boundedElastic still > 50ms (off event-loop, but heavy)",
                    ctx.updatedVehicles().size(), dispatchedCount, loopMicros, maxCallMicros);
        } else {
            log.debug("[GPS_PIPELINE] ON_GPS_UPDATE_LOOP_TIMING vehicles={} dispatched={} totalMicros={} maxCallMicros={}",
                    ctx.updatedVehicles().size(), dispatchedCount, loopMicros, maxCallMicros);
        }
    }

    private Mono<Void> persistRawGpsHistory(Context ctx) {
        long rawWinnersWithPosition = ctx.latestPositionsByDevice().values().stream()
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .count();

        if (rawWinnersWithPosition == 0) {
            return Mono.empty();
        }

        return Flux.fromIterable(ctx.latestPositionsByDevice().values())
                .filter(p -> p.getLatitude() != null && p.getLongitude() != null)
                .filter(p -> !ctx.frozenCoordsDeviceIds().contains(p.getDeviceId()))
                .flatMap(p -> gpsHistoryService.addPoint(
                        p.getDeviceId(),
                        p.getLatitude(),
                        p.getLongitude(),
                        p.getSpeed(),
                        p.getFixTime()
                ), 5)
                .then(Mono.fromRunnable(() ->
                        log.debug("[GPS_PIPELINE] Saved raw GPS history for {} winners (frozen-skipped={})",
                                rawWinnersWithPosition - ctx.frozenCoordsDeviceIds().size(),
                                ctx.frozenCoordsDeviceIds().size())));
    }

    private Vehicle applyGatekeeperDecision(Vehicle v, Map<String, double[]> oldCoordsByVehicleId) {
        String vehicleId = v.getId().getValue();
        GatekeeperDecision decision = predictionService.evaluateGate(vehicleId);
        double[] oldCoords = oldCoordsByVehicleId.get(vehicleId);
        if (decision.allowsCoordinateWrite()) {
            pipelineTracer.traceDbSave(
                    v.getDeviceId(), v.getLicensePlate(),
                    oldCoords != null ? oldCoords[0] : null,
                    oldCoords != null ? oldCoords[1] : null,
                    v.getCurrentLatitude(), v.getCurrentLongitude(),
                    true, decision.name());
            return v;
        }
        if (oldCoords == null) {
            pipelineTracer.traceDbSave(
                    v.getDeviceId(), v.getLicensePlate(),
                    null, null,
                    v.getCurrentLatitude(), v.getCurrentLongitude(),
                    true, decision.name() + "-noOldCoords");
            return v;
        }
        log.debug("[GPS_PIPELINE] DB_HEARTBEAT_ONLY vehicle={} plate={} decision={} — preserving coords, updating timing only",
                vehicleId, v.getLicensePlate(), decision);
        pipelineTracer.traceDbSave(
                v.getDeviceId(), v.getLicensePlate(),
                oldCoords[0], oldCoords[1],
                v.getCurrentLatitude(), v.getCurrentLongitude(),
                false, decision.name() + "-heartbeatOnly");
        return v.toBuilder()
                .currentLatitude(oldCoords[0])
                .currentLongitude(oldCoords[1])
                .build();
    }

    private void publishPositionEvent(Vehicle v, boolean hasSignificantChange, boolean frozenCoordsWithMotion) {
        String vehicleId = v.getId().getValue();
        GatekeeperDecision decision = predictionService.evaluateGate(vehicleId);
        boolean forcePublish = shouldForcePublishForVehicle(vehicleId);
        boolean shouldPublish = (hasSignificantChange || forcePublish) && !frozenCoordsWithMotion;

        if (!shouldPublish) {
            return;
        }

        lastPublishedTime.put(vehicleId, Instant.now());
        log.debug("[GPS_PIPELINE] WS_PUBLISH vehicle={} plate={} decision={} lat={} lon={} speed={} moved={}",
                vehicleId, v.getLicensePlate(), decision,
                v.getCurrentLatitude(), v.getCurrentLongitude(),
                v.getSpeedKmh(), hasSignificantChange);

        VehiclePositionUpdatedEvent event = new VehiclePositionUpdatedEvent(
                vehicleId,
                v.getDeviceId(),
                v.getLicensePlate(),
                v.getRouteNumber(),
                v.getCurrentLatitude(),
                v.getCurrentLongitude(),
                v.getSpeedKmh(),
                v.getIsInMotion(),
                v.getLastPositionUpdate(),
                v.getCourse(),
                v.getCurrentDirection()
        );
        domainEventPublisher.publish(event);
    }

    private boolean shouldForcePublishForVehicle(String vehicleId) {
        if (predictionService.isActivelyPredicting(vehicleId)) {
            return false;
        }
        Instant lastPublished = lastPublishedTime.get(vehicleId);
        if (lastPublished == null) {
            return true;
        }
        long secondsSinceLastPublish = Instant.now().getEpochSecond() - lastPublished.getEpochSecond();
        return secondsSinceLastPublish >= FORCE_PUBLISH_INTERVAL_SECONDS;
    }

    @Scheduled(fixedDelay = 60_000)
    void cleanupStalePublishTimes() {
        Instant cutoff = Instant.now().minusSeconds(300);
        int before = lastPublishedTime.size();
        lastPublishedTime.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int after = lastPublishedTime.size();
        if (before != after) {
            log.debug("[GPS_PIPELINE] lastPublishedTime cleanup: removed {} entries ({} -> {})",
                    before - after, before, after);
        }
    }
}
