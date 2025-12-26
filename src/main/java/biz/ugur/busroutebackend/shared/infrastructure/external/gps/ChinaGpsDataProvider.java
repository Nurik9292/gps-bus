package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.transport.application.dto.GpsApiResponseDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.GpsDataProvider;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@Component
@Slf4j
@ConditionalOnProperty(prefix = "external.api.gps", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChinaGpsDataProvider implements GpsDataProvider {

    private static final DateTimeFormatter REPORT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter API_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private static final ZoneId TURKMENISTAN_ZONE = ZoneId.of("Asia/Ashgabat");

    private final WebClient webClient;
    private final String token;
    private final int batchSize;
    private final int maxConcurrentBatches;
    private final int timeWindowMinutes;
    private final boolean enabled;

    public ChinaGpsDataProvider(
            @Qualifier("gpsApiClient") WebClient webClient,
            @Value("${external.api.gps.token}") String token,
            @Value("${external.api.gps.batch-size:50}") int batchSize,
            @Value("${external.api.gps.max-concurrent-batches:3}") int maxConcurrentBatches,
            @Value("${external.api.gps.time-window-minutes:60}") int timeWindowMinutes,
            @Value("${external.api.gps.enabled:true}") boolean enabled) {
        this.webClient = webClient;
        this.token = token;
        this.batchSize = batchSize;
        this.maxConcurrentBatches = maxConcurrentBatches;
        this.timeWindowMinutes = timeWindowMinutes;
        this.enabled = enabled;

        log.info("ChinaGpsDataProvider initialized: enabled={}, batchSize={}, maxConcurrentBatches={}, timeWindowMinutes={}",
                enabled, batchSize, maxConcurrentBatches, timeWindowMinutes);
    }

    @Override
    public GpsProviderType getProviderType() {
        return GpsProviderType.CHINA;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    public Mono<List<GpsPositionDTO>> fetchPositionsByDeviceIds(List<String> deviceIds) {
        if (!enabled) {
            log.debug("China GPS provider is disabled, returning empty list");
            return Mono.just(List.of());
        }

        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        Instant to = Instant.now();
        Instant from = to.minus(timeWindowMinutes, ChronoUnit.MINUTES);

        List<List<String>> batches = partitionList(deviceIds, batchSize);
        log.debug("[CHINA] Split {} device IDs into {} batches (batch size: {})",
                deviceIds.size(), batches.size(), batchSize);

        return Flux.fromIterable(batches)
                .flatMap(batch -> fetchBatch(batch, from, to), maxConcurrentBatches)
                .flatMapIterable(list -> list)
                .collectList()
                .doOnSuccess(positions ->
                        log.info("[CHINA] Successfully fetched {} GPS positions from {} batches",
                                positions.size(), batches.size()))
                .doOnError(error ->
                        log.error("[CHINA] Failed to fetch GPS positions for {} devices", deviceIds.size(), error));
    }

    @Override
    public Mono<List<GpsPositionDTO>> fetchAllPositions() {
        log.debug("[CHINA] fetchAllPositions called - this API requires device IDs");
        return Mono.just(List.of());
    }

    @Override
    public Mono<Boolean> healthCheck() {
        if (!enabled) {
            return Mono.just(false);
        }

        log.debug("[CHINA] Performing health check");

        Instant to = Instant.now();
        Instant from = to.minus(1, ChronoUnit.MINUTES);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/vehicleinfo/v1/getVehicleData")
                        .queryParam("from", from.toString())
                        .queryParam("to", to.toString())
                        .build())
                .header("token", token)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("[CHINA] Health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }

    private Mono<List<GpsPositionDTO>> fetchBatch(List<String> deviceIds, Instant from, Instant to) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        ZonedDateTime fromLocal = from.atZone(TURKMENISTAN_ZONE);
        ZonedDateTime toLocal = to.atZone(TURKMENISTAN_ZONE);

        String fromStr = fromLocal.toLocalDateTime().format(API_TIME_FORMATTER);
        String toStr = toLocal.toLocalDateTime().format(API_TIME_FORMATTER);

        log.debug("[CHINA] Batch request: from={}, to={}, devices={}",
                fromStr, toStr, deviceIds.size());

        return webClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder
                            .path("/api/vehicleinfo/v1/getVehicleData")
                            .queryParam("from", fromStr)
                            .queryParam("to", toStr);

                    for (String id : deviceIds) {
                        builder.queryParam("id", id);
                    }

                    return builder.build();
                })
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(GpsApiResponseDTO.class)
                .map(response -> {
                    if (!response.isSuccess()) {
                        log.warn("[CHINA] API returned non-success code: {}, msg: {}, traceId: {}",
                                response.getCode(), response.getMsg(), response.getTraceId());
                    }

                    List<GpsPositionDTO> allPositions = response.getData();
                    if (allPositions == null || allPositions.isEmpty()) {
                        log.debug("[CHINA] No data returned for batch of {} devices", deviceIds.size());
                        return List.<GpsPositionDTO>of();
                    }

                    List<GpsPositionDTO> latestPositions = getLatestPositionsByUniqueId(allPositions);
                    latestPositions.forEach(this::transformPosition);

                    log.debug("[CHINA] Batch result: {} total records -> {} unique vehicles",
                            allPositions.size(), latestPositions.size());

                    return latestPositions;
                })
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.warn("[CHINA] Retrying batch request for {} devices, attempt {}/3",
                                        deviceIds.size(), retrySignal.totalRetries() + 1)))
                .onErrorResume(error -> {
                    log.error("[CHINA] Failed to fetch batch of {} devices: {}",
                            deviceIds.size(), error.getMessage());
                    return Mono.just(List.of());
                });
    }

    private List<GpsPositionDTO> getLatestPositionsByUniqueId(List<GpsPositionDTO> positions) {
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }

        Map<String, GpsPositionDTO> latestByUniqueId = positions.stream()
                .filter(pos -> pos.getAttributes() != null
                        && pos.getAttributes().getUniqueId() != null
                        && pos.getReportTime() != null)
                .collect(Collectors.toMap(
                        pos -> pos.getAttributes().getUniqueId(),
                        pos -> pos,
                        (existing, replacement) ->
                                compareReportTime(existing.getReportTime(), replacement.getReportTime()) >= 0
                                        ? existing
                                        : replacement
                ));

        return new ArrayList<>(latestByUniqueId.values());
    }

    private int compareReportTime(String time1, String time2) {
        try {
            LocalDateTime dt1 = LocalDateTime.parse(time1, REPORT_TIME_FORMATTER);
            LocalDateTime dt2 = LocalDateTime.parse(time2, REPORT_TIME_FORMATTER);
            return dt1.compareTo(dt2);
        } catch (Exception e) {
            log.warn("[CHINA] Failed to parse reportTime: {} or {}", time1, time2);
            return time1.compareTo(time2);
        }
    }

    private void transformPosition(GpsPositionDTO position) {
        if (position.getUtcTime() != null && position.getFixTime() == null) {
            try {
                position.setFixTime(LocalDateTime.parse(
                        position.getUtcTime(),
                        DateTimeFormatter.ISO_DATE_TIME));
            } catch (Exception e) {
                log.warn("[CHINA] Failed to parse utcTime: {}", position.getUtcTime());
            }
        }

        if (position.getDeviceId() == null &&
                position.getAttributes() != null &&
                position.getAttributes().getUniqueId() != null) {
            position.setDeviceId(position.getAttributes().getUniqueId());
        }
    }

    private <T> List<List<T>> partitionList(List<T> list, int batchSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            partitions.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return partitions;
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return !ex.getStatusCode().is4xxClientError();
        }
        return true;
    }
}
