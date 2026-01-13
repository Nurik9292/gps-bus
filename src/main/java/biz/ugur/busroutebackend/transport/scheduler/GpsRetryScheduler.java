package biz.ugur.busroutebackend.transport.scheduler;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.usecase.UpdateVehiclePositionsUseCase;
import biz.ugur.busroutebackend.transport.domain.valueobject.FailedGpsUpdate;
import biz.ugur.busroutebackend.transport.infrastructure.config.GpsDeadLetterQueueProperties;
import biz.ugur.busroutebackend.transport.infrastructure.redis.GpsUpdateDeadLetterQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "business.gps-dlq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GpsRetryScheduler {

    private final GpsUpdateDeadLetterQueue deadLetterQueue;
    private final GpsDeadLetterQueueProperties properties;
    private final UpdateVehiclePositionsUseCase updateUseCase;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    public GpsRetryScheduler(
            GpsUpdateDeadLetterQueue deadLetterQueue,
            GpsDeadLetterQueueProperties properties,
            UpdateVehiclePositionsUseCase updateUseCase) {
        this.deadLetterQueue = deadLetterQueue;
        this.properties = properties;
        this.updateUseCase = updateUseCase;

        log.info("GpsRetryScheduler initialized: enabled={}, maxRetries={}, batchSize={}",
                properties.isEnabled(), properties.getMaxRetries(), properties.getRetryBatchSize());
    }

    @Scheduled(fixedDelayString = "${business.gps-dlq.retry-interval:60000}")
    public void processFailedUpdates() {
        if (!properties.isEnabled()) {
            return;
        }

        if (!isRunning.compareAndSet(false, true)) {
            log.debug("GPS retry scheduler already running, skipping this execution");
            return;
        }

        log.debug("Starting GPS DLQ retry processing");

        deadLetterQueue.getPendingForRetry()
                .flatMap(this::retryUpdate)
                .count()
                .doOnSuccess(count -> {
                    if (count > 0) {
                        log.info("GPS_DLQ_RETRY: Processed {} failed updates", count);
                    }
                })
                .doOnError(error -> log.error("GPS_DLQ_RETRY: Error processing failed updates", error))
                .doFinally(signal -> isRunning.set(false))
                .subscribe();
    }

    private Mono<Boolean> retryUpdate(FailedGpsUpdate failedUpdate) {
        if (!failedUpdate.hasValidCoordinates()) {
            log.debug("GPS_DLQ_RETRY: Skipping update without coordinates for device {}",
                    failedUpdate.deviceId());
            return deadLetterQueue.markRetried(failedUpdate).thenReturn(false);
        }

        GpsPositionDTO position = convertToDto(failedUpdate);

        return updateUseCase.execute(List.of(position))
                .flatMap(result -> {
                    if (result.failedCount() == 0) {
                        log.info("GPS_DLQ_RETRY: Successfully retried update for device {} (attempt #{})",
                                failedUpdate.deviceId(), failedUpdate.retryCount() + 1);
                        return deadLetterQueue.markRetried(failedUpdate).thenReturn(true);
                    } else {
                        log.warn("GPS_DLQ_RETRY: Retry failed for device {} (attempt #{}/{})",
                                failedUpdate.deviceId(),
                                failedUpdate.retryCount() + 1,
                                properties.getMaxRetries());
                        return deadLetterQueue.requeue(failedUpdate).thenReturn(false);
                    }
                })
                .onErrorResume(error -> {
                    log.error("GPS_DLQ_RETRY: Error retrying update for device {}: {}",
                            failedUpdate.deviceId(), error.getMessage());
                    return deadLetterQueue.requeue(failedUpdate).thenReturn(false);
                });
    }

    private GpsPositionDTO convertToDto(FailedGpsUpdate failedUpdate) {
        GpsPositionDTO dto = new GpsPositionDTO();
        dto.setDeviceId(failedUpdate.deviceId());
        dto.setLatitude(failedUpdate.latitude());
        dto.setLongitude(failedUpdate.longitude());
        dto.setSpeed(failedUpdate.speed());
        dto.setCourse(failedUpdate.course());
        dto.setFixTime(failedUpdate.fixTime());

        if (failedUpdate.licensePlate() != null) {
            GpsPositionDTO.GpsAttributesDTO attributes = new GpsPositionDTO.GpsAttributesDTO();
            attributes.setName(failedUpdate.licensePlate());
            dto.setAttributes(attributes);
        }

        return dto;
    }

    public Mono<GpsUpdateDeadLetterQueue.DlqStats> getStats() {
        return deadLetterQueue.getStats();
    }
}
