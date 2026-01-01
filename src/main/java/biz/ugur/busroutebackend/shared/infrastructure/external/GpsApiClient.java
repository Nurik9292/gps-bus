package biz.ugur.busroutebackend.shared.infrastructure.external;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto.ChinaGpsRequestDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsApiResponseDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component("gpsApiClientComponent")
@Slf4j
public class GpsApiClient {

    private static final String API_PATH = "/api/vehicleinfo/v1/tkm/getVehicleRealTimeData";
    private static final DateTimeFormatter REPORT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebClient webClient;
    private final String token;

    public GpsApiClient(@Qualifier("gpsApiClient") WebClient webClient,
                        @Value("${external.api.gps.token}") String token) {
        this.webClient = webClient;
        this.token = token;

        log.info("GpsApiClient initialized with new real-time API");
    }

    public Mono<List<GpsPositionDTO>> fetchVehiclePositionsByIds(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        log.debug("Fetching real-time GPS positions for {} devices", deviceIds.size());

        ChinaGpsRequestDTO request = ChinaGpsRequestDTO.fromDeviceIds(deviceIds);

        return webClient.post()
                .uri(API_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GpsApiResponseDTO.class)
                .map(response -> processResponse(response, deviceIds.size()))
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying GPS API request for {} devices, attempt {}/3: {}",
                                        deviceIds.size(),
                                        retrySignal.totalRetries() + 1,
                                        retrySignal.failure().getMessage())))
                .doOnSuccess(positions ->
                        log.info("Successfully fetched {} GPS positions for {} requested devices",
                                positions.size(), deviceIds.size()))
                .onErrorResume(error -> {
                    String errorDetails = buildErrorDetails(error);
                    log.error("Failed to fetch GPS positions for {} devices: {} - {}. Details: {}",
                            deviceIds.size(),
                            error.getClass().getSimpleName(),
                            error.getMessage(),
                            errorDetails);
                    return Mono.just(List.of());
                });
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

    public Mono<Boolean> healthCheck() {
        log.debug("Performing GPS API health check");

        ChinaGpsRequestDTO request = new ChinaGpsRequestDTO("");

        return webClient.post()
                .uri(API_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("GPS API health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }

    private List<GpsPositionDTO> processResponse(GpsApiResponseDTO response, int requestedCount) {
        if (!response.isSuccess()) {
            log.warn("GPS API returned non-success code: {}, msg: {}, traceId: {}",
                    response.getCode(), response.getMsg(), response.getTraceId());
        }

        List<GpsPositionDTO> allPositions = response.getData();
        if (allPositions == null || allPositions.isEmpty()) {
            log.debug("No data returned for {} requested devices", requestedCount);
            return List.of();
        }

        List<GpsPositionDTO> latestPositions = getLatestPositionsByUniqueId(allPositions);
        latestPositions.forEach(this::transformPosition);

        log.debug("Processed {} total records -> {} unique vehicles",
                allPositions.size(), latestPositions.size());

        return latestPositions;
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

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return !ex.getStatusCode().is4xxClientError();
        }
        return true;
    }

    private String buildErrorDetails(Throwable error) {
        if (error instanceof WebClientResponseException ex) {
            if (ex.getCause() instanceof io.netty.handler.timeout.ReadTimeoutException) {
                return String.format("HTTP %d received but ReadTimeout while reading response body",
                        ex.getStatusCode().value());
            }
            return String.format("HTTP %d - %s, Body: %s",
                    ex.getStatusCode().value(),
                    ex.getStatusText(),
                    ex.getResponseBodyAsString());
        } else if (error instanceof io.netty.handler.timeout.ReadTimeoutException) {
            return "ReadTimeout - GPS API is too slow to respond";
        } else if (error instanceof io.netty.handler.timeout.WriteTimeoutException) {
            return "WriteTimeout - Failed to send request to GPS API";
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
