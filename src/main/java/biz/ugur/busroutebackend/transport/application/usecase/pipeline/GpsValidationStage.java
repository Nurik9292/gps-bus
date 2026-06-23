package biz.ugur.busroutebackend.transport.application.usecase.pipeline;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.VehicleValidationService;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsValidationResult;
import biz.ugur.busroutebackend.transport.infrastructure.metrics.GpsValidationMetricsRecorder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class GpsValidationStage {

    private static final long MAX_GPS_AGE_SECONDS = 120;
    private static final long MAX_GPS_FUTURE_SKEW_SECONDS = 60;
    private static final long IDEMPOTENCY_TTL_SECONDS = 60;

    private final VehicleValidationService validationService;
    private final GpsValidationMetricsRecorder validationMetricsRecorder;

    private final ConcurrentHashMap<String, LocalDateTime> lastProcessedFixTimeByDevice = new ConcurrentHashMap<>();

    public GpsValidationStage(VehicleValidationService validationService,
                              GpsValidationMetricsRecorder validationMetricsRecorder) {
        this.validationService = validationService;
        this.validationMetricsRecorder = validationMetricsRecorder;
    }

    public record ValidatedGpsBatch(List<GpsPositionDTO> accepted, int invalidCount) {
        public boolean isEmpty() {
            return accepted.isEmpty();
        }
    }

    public ValidatedGpsBatch apply(List<GpsPositionDTO> gpsPositions) {
        List<GpsPositionDTO> validPositions = new ArrayList<>();
        Map<GpsValidationResult, Long> validationCounts = new EnumMap<>(GpsValidationResult.class);
        for (GpsValidationResult r : GpsValidationResult.values()) {
            validationCounts.put(r, 0L);
        }

        for (GpsPositionDTO position : gpsPositions) {
            GpsValidationResult result = validationService.validateGpsPosition(position);
            validationCounts.merge(result, 1L, Long::sum);
            if (result.isValid()) {
                validPositions.add(position);
            }
        }

        validationMetricsRecorder.recordBatch(validationCounts);

        long invalidCount = gpsPositions.size() - validPositions.size();
        log.debug("[GPS_PIPELINE] VALIDATION total={} valid={} invalid={}",
                gpsPositions.size(), validPositions.size(), invalidCount);

        if (validPositions.isEmpty()) {
            log.warn("[GPS_PIPELINE] All positions failed validation: {}", validationCounts);
            return new ValidatedGpsBatch(List.of(), gpsPositions.size());
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime staleCutoff = now.minusSeconds(MAX_GPS_AGE_SECONDS);
        LocalDateTime futureCutoff = now.plusSeconds(MAX_GPS_FUTURE_SKEW_SECONDS);
        List<GpsPositionDTO> freshPositions = validPositions.stream()
                .filter(pos -> pos.getFixTime() != null
                        && !pos.getFixTime().isBefore(staleCutoff)
                        && !pos.getFixTime().isAfter(futureCutoff))
                .toList();
        if (freshPositions.size() < validPositions.size()) {
            log.debug("[GPS_PIPELINE] STALE_FILTER dropped={}/{} (age > {}s)",
                    validPositions.size() - freshPositions.size(), validPositions.size(), MAX_GPS_AGE_SECONDS);
        }
        log.debug("[GPS_PIPELINE] STALE_FILTER passed={} dropped={}",
                freshPositions.size(), validPositions.size() - freshPositions.size());
        if (freshPositions.isEmpty()) {
            return new ValidatedGpsBatch(List.of(), validPositions.size());
        }

        List<GpsPositionDTO> highQualityPositions = freshPositions.stream()
                .filter(pos -> {
                    if (pos.isHighQualityFix()) {
                        return true;
                    }
                    log.debug("[GPS_PIPELINE] QUALITY_REJECTED device={} hdop={} sat={}",
                            pos.getDeviceId(), pos.getHdop(), pos.getSatellites());
                    return false;
                })
                .toList();
        int qualityDropped = freshPositions.size() - highQualityPositions.size();
        if (qualityDropped > 0) {
            log.debug("[GPS_PIPELINE] QUALITY_FILTER passed={} rejected={}",
                    highQualityPositions.size(), qualityDropped);
        }
        if (highQualityPositions.isEmpty()) {
            return new ValidatedGpsBatch(List.of(), freshPositions.size());
        }

        List<GpsPositionDTO> idempotencyFiltered = highQualityPositions.stream()
                .filter(pos -> {
                    String deviceId = pos.getDeviceId();
                    LocalDateTime fixTime = pos.getFixTime();
                    if (deviceId == null || fixTime == null) {
                        return true;
                    }
                    LocalDateTime lastSeen = lastProcessedFixTimeByDevice.get(deviceId);
                    return lastSeen == null || fixTime.isAfter(lastSeen);
                })
                .toList();
        int duplicateDropped = highQualityPositions.size() - idempotencyFiltered.size();
        if (duplicateDropped > 0) {
            log.debug("[GPS_PIPELINE] IDEMPOTENCY_FILTER passed={} duplicates_skipped={}",
                    idempotencyFiltered.size(), duplicateDropped);
        }
        if (idempotencyFiltered.isEmpty()) {
            log.debug("[GPS_PIPELINE] CYCLE_SKIPPED_NO_FRESH_DATA all {} positions already processed",
                    highQualityPositions.size());
            return new ValidatedGpsBatch(List.of(), 0);
        }

        for (GpsPositionDTO pos : idempotencyFiltered) {
            if (pos.getDeviceId() != null && pos.getFixTime() != null) {
                lastProcessedFixTimeByDevice.merge(pos.getDeviceId(), pos.getFixTime(),
                        (existing, incoming) -> incoming.isAfter(existing) ? incoming : existing);
            }
        }

        return new ValidatedGpsBatch(idempotencyFiltered, gpsPositions.size() - idempotencyFiltered.size());
    }

    @Scheduled(fixedDelay = 60_000)
    void cleanupStaleIdempotencyEntries() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(IDEMPOTENCY_TTL_SECONDS);
        int before = lastProcessedFixTimeByDevice.size();
        lastProcessedFixTimeByDevice.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
        int after = lastProcessedFixTimeByDevice.size();
        if (before != after) {
            log.debug("[GPS_PIPELINE] idempotency cleanup: removed {} entries ({} -> {})",
                    before - after, before, after);
        }
    }
}
