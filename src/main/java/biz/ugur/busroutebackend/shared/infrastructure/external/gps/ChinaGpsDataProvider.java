package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto.ChinaGpsRequestDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsApiResponseDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.GpsDataProvider;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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


@Component
@Slf4j
@ConditionalOnProperty(prefix = "external.api.gps", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChinaGpsDataProvider implements GpsDataProvider {

    private static final String API_PATH = "/api/vehicleinfo/v1/tkm/getVehicleRealTimeData";
    private static final DateTimeFormatter REPORT_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WebClient webClient;
    private final String token;
    private final boolean enabled;

    public ChinaGpsDataProvider(
            @Qualifier("gpsApiClient") WebClient webClient,
            @Value("${external.api.gps.token}") String token,
            @Value("${external.api.gps.enabled:true}") boolean enabled) {
        this.webClient = webClient;
        this.token = token;
        this.enabled = enabled;

        log.info("ChinaGpsDataProvider initialized: enabled={}", enabled);
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
            log.debug("[CHINA] GPS provider is disabled, returning empty list");
            return Mono.just(List.of());
        }

        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        log.debug("[CHINA] Fetching real-time positions for {} devices", deviceIds.size());

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
                                log.warn("[CHINA] Retrying API request, attempt {}/3",
                                        retrySignal.totalRetries() + 1)))
                .doOnSuccess(positions ->
                        log.info("[CHINA] Successfully fetched {} GPS positions for {} requested devices",
                                positions.size(), deviceIds.size()))
                .onErrorResume(error -> {
                    log.error("[CHINA] Failed to fetch GPS positions for {} devices: {}",
                            deviceIds.size(), error.getMessage());
                    return Mono.just(List.of());
                });
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
                .doOnNext(healthy -> log.info("[CHINA] Health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
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

        log.debug("[CHINA] Processed {} total records -> {} unique vehicles",
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

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            return !ex.getStatusCode().is4xxClientError();
        }
        return true;
    }
}
