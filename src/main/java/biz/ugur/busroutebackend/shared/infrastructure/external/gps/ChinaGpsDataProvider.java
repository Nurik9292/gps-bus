package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto.ChinaGpsRequestDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsApiResponseDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;


@Component
@Slf4j
@ConditionalOnProperty(prefix = "external.api.gps", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChinaGpsDataProvider extends AbstractGpsDataProvider {

    private static final String API_PATH = "/api/vehicleinfo/v1/tkm/getVehicleRealTimeData";
    private static final DateTimeFormatter REPORT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ISO_OFFSET_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final String token;

    public ChinaGpsDataProvider(
            @Qualifier("gpsApiClient") WebClient webClient,
            GpsProviderProperties properties,
            @Value("${external.api.gps.token}") String token,
            @Value("${external.api.gps.enabled:true}") boolean enabled) {
        super(webClient, properties, enabled);
        this.token = token;

        log.info("ChinaGpsDataProvider initialized: enabled={}", enabled);
    }

    @Override
    public GpsProviderType getProviderType() {
        return GpsProviderType.CHINA;
    }

    @Override
    public int getPriority() {
        return 10;
    }

    @Override
    @CircuitBreaker(name = "gpsApi", fallbackMethod = "fetchPositionsFallback")
    public Mono<List<GpsPositionDTO>> fetchPositionsByDeviceIds(List<String> deviceIds) {
        if (!enabled) {
            return handleDisabled();
        }

        if (deviceIds == null || deviceIds.isEmpty()) {
            return handleEmptyRequest();
        }

        logFetch(deviceIds.size());
        log.debug("[GPS_PIPELINE] CHINA_FETCH_START devices={}", deviceIds.size());

        ChinaGpsRequestDTO request = ChinaGpsRequestDTO.fromDeviceIds(deviceIds);

        Set<String> requestedDeviceIds = new HashSet<>(deviceIds);

        return webClient.post()
                .uri(API_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(GpsApiResponseDTO.class)
                .map(response -> processResponse(response, deviceIds.size()))
                .timeout(properties.getTimeout().getRequest())
                .retryWhen(createRetrySpec())
                .doOnSuccess(positions -> {
                    logSuccess(positions.size(), deviceIds.size());
                    logMissingDevices(requestedDeviceIds, positions);
                    int missing = requestedDeviceIds.size() - positions.size();
                    log.debug("[GPS_PIPELINE] CHINA_FETCH_DONE received={} requested={} missing={}",
                            positions.size(), deviceIds.size(), missing);
                })
                .onErrorResume(error -> handleError(error, deviceIds.size()));
    }

    @SuppressWarnings("unused")
    private Mono<List<GpsPositionDTO>> fetchPositionsFallback(List<String> deviceIds, Throwable throwable) {
        log.warn("[CHINA] Circuit breaker fallback triggered for {} devices: {}",
                deviceIds != null ? deviceIds.size() : 0, throwable.getMessage());
        return Mono.just(List.of());
    }

    @Override
    public Mono<List<GpsPositionDTO>> fetchAllPositions() {
        log.debug("[CHINA] fetchAllPositions called - this API requires device IDs");
        return Mono.just(List.of());
    }

    @Override
    @CircuitBreaker(name = "gpsApi", fallbackMethod = "healthCheckFallback")
    public Mono<Boolean> healthCheck() {
        if (!enabled) {
            return Mono.just(false);
        }

        log.debug("[CHINA] Performing health check");

        ChinaGpsRequestDTO request = new ChinaGpsRequestDTO("");

        return webClient.post()
                .uri(API_PATH)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .bodyValue(request)
                .retrieve()
                .toBodilessEntity()
                .timeout(properties.getTimeout().getHealthCheck())
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("[CHINA] Health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }

    @SuppressWarnings("unused")
    private Mono<Boolean> healthCheckFallback(Throwable throwable) {
        log.warn("[CHINA] Health check circuit breaker fallback: {}", throwable.getMessage());
        return Mono.just(false);
    }

    private List<GpsPositionDTO> processResponse(GpsApiResponseDTO response, int requestedCount) {
        if (!response.isSuccess()) {
            log.warn("[CHINA] API returned non-success code: {}, msg: {}, traceId: {}",
                    response.getCode(), response.getMsg(), response.getTraceId());
        }

        List<GpsPositionDTO> allPositions = response.getData();
        if (allPositions == null || allPositions.isEmpty()) {
            log.debug("[CHINA] No data returned for {} requested devices", requestedCount);
            return List.of();
        }

        List<GpsPositionDTO> latestPositions = getLatestPositionsByUniqueId(allPositions);
        latestPositions.forEach(this::transformPosition);

        log.debug("[GPS_PIPELINE] CHINA_DEDUP raw={} unique={}",
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
            log.warn("[CHINA] Failed to parse reportTime: {} or {}", time1, time2);
            return time1.compareTo(time2);
        }
    }

    private void logMissingDevices(Set<String> requestedDeviceIds, List<GpsPositionDTO> positions) {
        Set<String> returnedDeviceIds = positions.stream()
                .map(GpsPositionDTO::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<String> missingDeviceIds = new HashSet<>(requestedDeviceIds);
        missingDeviceIds.removeAll(returnedDeviceIds);

        if (!missingDeviceIds.isEmpty()) {
            log.debug("[CHINA] {} devices not returned by API", missingDeviceIds.size());
        }
    }

    private void transformPosition(GpsPositionDTO position) {
        if (position.getUtcTime() != null) {
            LocalDateTime utc = parseAsUtc(position.getUtcTime());
            if (utc != null) {
                position.setFixTime(utc);
            }
        }

        if (position.getDeviceId() == null &&
                position.getAttributes() != null &&
                position.getAttributes().getUniqueId() != null) {
            position.setDeviceId(position.getAttributes().getUniqueId());
        }
    }

 
    private LocalDateTime parseAsUtc(String timeStr) {
        if (timeStr == null || timeStr.isBlank()) return null;
        try {
            return OffsetDateTime.parse(timeStr, ISO_OFFSET_FORMATTER)
                    .withOffsetSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();
        } catch (DateTimeParseException e1) {
            try {
                return LocalDateTime.parse(timeStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException e2) {
                log.warn("[CHINA] Cannot parse utcTime '{}': {}", timeStr, e2.getMessage());
                return null;
            }
        }
    }
}
