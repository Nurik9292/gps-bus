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
import java.time.ZoneId;
import java.time.ZonedDateTime;
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

    // API expects format: 2023-10-01T12:00:00Z (ISO-8601 with Z suffix)
    private static final DateTimeFormatter API_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    private static final ZoneId TURKMENISTAN_ZONE = ZoneId.of("Asia/Ashgabat");

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

        ZonedDateTime fromLocal = from.atZone(TURKMENISTAN_ZONE);
        ZonedDateTime toLocal = to.atZone(TURKMENISTAN_ZONE);

        String fromStr = fromLocal.toLocalDateTime().format(API_TIME_FORMATTER);
        String toStr = toLocal.toLocalDateTime().format(API_TIME_FORMATTER);

        log.debug("GPS API request: from={}, to={}, devices={} (UTC: {} to {})",
                fromStr, toStr, deviceIds.size(), from, to);

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
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal -> {
                            Throwable failure = retrySignal.failure();
                            log.warn("Retrying GPS API batch request for {} devices, attempt {}/{}: {} - {}",
                                    deviceIds.size(),
                                    retrySignal.totalRetries() + 1, 3,
                                    failure.getClass().getSimpleName(),
                                    failure.getMessage());
                        }))
                .onErrorResume(error -> {
                    String errorDetails = buildErrorDetails(error);
                    log.error("Failed to fetch GPS batch of {} devices after 3 retries: {} - {}. Details: {}",
                            deviceIds.size(),
                            error.getClass().getSimpleName(),
                            error.getMessage(),
                            errorDetails,
                            error);
                    return Mono.just(List.of());
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

    private String buildErrorDetails(Throwable error) {
        if (error instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) error;

            // Check if the cause is ReadTimeoutException
            if (ex.getCause() instanceof io.netty.handler.timeout.ReadTimeoutException) {
                return String.format("HTTP %d received but ReadTimeout while reading response body (GPS API is responding slowly). Consider increasing read-timeout.",
                        ex.getStatusCode().value());
            }

            return String.format("HTTP %d - %s, Body: %s",
                    ex.getStatusCode().value(),
                    ex.getStatusText(),
                    ex.getResponseBodyAsString());
        } else if (error instanceof io.netty.handler.timeout.ReadTimeoutException) {
            return "ReadTimeout - GPS API is too slow to send response data. Consider increasing read-timeout setting.";
        } else if (error instanceof io.netty.handler.timeout.WriteTimeoutException) {
            return "WriteTimeout - Failed to send request data to GPS API in time";
        } else if (error instanceof java.util.concurrent.TimeoutException) {
            return "Request timeout - GPS API didn't respond in time";
        } else if (error instanceof java.net.ConnectException) {
            return "Connection refused - GPS API server may be down";
        } else if (error instanceof java.io.IOException) {
            return "Network I/O error: " + error.getMessage();
        }
        return error.getClass().getName();
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