package biz.ugur.busroutebackend.shared.infrastructure.external;

import biz.ugur.busroutebackend.transport.application.dto.GpsApiResponseDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("gpsApiClientComponent")
@Slf4j
public class GpsApiClient {

    private static final DateTimeFormatter REPORT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebClient webClient;
    private final String token;
    private final int batchSize;
    private final int maxConcurrentBatches;
    private final int timeWindowMinutes;
    private final ObjectMapper objectMapper;

    public GpsApiClient(@Qualifier("gpsApiClient") WebClient webClient,
                        @Value("${external.api.gps.token}") String token,
                        @Value("${external.api.gps.batch-size:50}") int batchSize,
                        @Value("${external.api.gps.max-concurrent-batches:3}") int maxConcurrentBatches,
                        @Value("${external.api.gps.time-window-minutes:3}") int timeWindowMinutes,
                        ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.token = token;
        this.batchSize = batchSize;
        this.maxConcurrentBatches = maxConcurrentBatches;
        this.timeWindowMinutes = timeWindowMinutes;
        this.objectMapper = objectMapper;

        log.info("GpsApiClient initialized: batchSize={}, maxConcurrentBatches={}, timeWindowMinutes={}",
                batchSize, maxConcurrentBatches, timeWindowMinutes);
    }


    public Mono<List<GpsPositionDTO>> fetchVehiclePositionsByIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        Instant to = Instant.now();
        Instant from = to.minus(timeWindowMinutes, ChronoUnit.MINUTES);

        List<List<String>> batches = partitionList(deviceIds, batchSize);
        log.debug("Split {} device IDs into {} batches (batch size: {})",
                deviceIds.size(), batches.size(), batchSize);

        return Flux.fromIterable(batches)
                .flatMap(batch -> fetchBatch(batch, from, to), maxConcurrentBatches)
                .flatMapIterable(list -> list)
                .collectList()
                .doOnSuccess(positions ->
                        log.info("Successfully fetched {} GPS positions from {} batches",
                                positions.size(), batches.size()))
                .doOnError(error ->
                        log.error("Failed to fetch GPS positions for {} devices", deviceIds.size(), error));
    }


    public Mono<GpsPositionDTO> fetchVehiclePosition(String deviceId) {
        log.debug("Fetching GPS position for device: {}", deviceId);

        return fetchVehiclePositionsByIds(List.of(deviceId))
                .flatMap(positions -> {
                    if (positions.isEmpty()) {
                        return Mono.error(new GpsApiException("Vehicle not found: " + deviceId));
                    }
                    return Mono.just(positions.getFirst());
                })
                .doOnSuccess(position ->
                        log.debug("Found position for device {}: ({}, {})",
                                deviceId, position.getLatitude(), position.getLongitude()));
    }



    private Mono<List<GpsPositionDTO>> fetchBatch(List<String> deviceIds, Instant from, Instant to) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }


        String fromStr = from.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        String toStr = to.truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();

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
                        log.warn("GPS API returned non-success code: {}, msg: {}, traceId: {}",
                                response.getCode(), response.getMsg(), response.getTraceId());
                    }

                    List<GpsPositionDTO> allPositions = response.getData();
                    if (allPositions == null || allPositions.isEmpty()) {
                        log.debug("No data returned for batch of {} devices", deviceIds.size());
                        return List.<GpsPositionDTO>of();
                    }

                    List<GpsPositionDTO> latestPositions = getLatestPositionsByUniqueId(allPositions);

                    latestPositions.forEach(this::transformPosition);

                    log.debug("Batch result: {} total records -> {} unique vehicles",
                            allPositions.size(), latestPositions.size());

                    return latestPositions;
                })
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying batch request, attempt: {}", retrySignal.totalRetries() + 1)))
                .onErrorResume(error -> {
                    log.error("Failed to fetch batch of {} devices: {}", deviceIds.size(), error.getMessage());
                    return Mono.just(List.of());  // Возвращаем пустой список при ошибке
                });
    }

    public Mono<Boolean> healthCheck() {
        log.debug("Performing GPS API health check");

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
                .doOnNext(healthy -> log.info("GPS API health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
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
                        // В случае дубликатов - берем с более поздним reportTime
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
            log.warn("Failed to parse reportTime: {} or {}", time1, time2);
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
                log.warn("Failed to parse utcTime: {}", position.getUtcTime());
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
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            return !ex.getStatusCode().is4xxClientError();
        }
        return true;
    }

    private GpsApiException mapToGpsApiException(Throwable throwable) {
        if (throwable instanceof GpsApiException) {
            return (GpsApiException) throwable;
        }

        String message = "GPS API integration error: " + throwable.getMessage();
        return new GpsApiException(message, throwable);
    }

    public static class GpsApiException extends RuntimeException {
        public GpsApiException(String message) {
            super(message);
        }

        public GpsApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}