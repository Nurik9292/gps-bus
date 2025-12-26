package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto.TugdkGpsResponseDTO;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.mapper.TugdkGpsPositionMapper;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.GpsDataProvider;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;
import java.util.Set;


@Component
@Slf4j
@ConditionalOnProperty(prefix = "external.api.gps-tugdk", name = "enabled", havingValue = "true")
public class TugdkGpsDataProvider implements GpsDataProvider {

    private final WebClient webClient;
    private final String token;
    private final TugdkGpsPositionMapper mapper;
    private final boolean enabled;
    private final Duration cacheTimeout;

    private volatile List<GpsPositionDTO> cachedPositions = List.of();
    private volatile long cacheTimestamp = 0;

    public TugdkGpsDataProvider(
            @Qualifier("tugdkGpsWebClient") WebClient webClient,
            @Value("${external.api.gps-tugdk.token}") String token,
            @Value("${external.api.gps-tugdk.enabled:false}") boolean enabled,
            @Value("${external.api.gps-tugdk.cache-timeout:5s}") Duration cacheTimeout,
            TugdkGpsPositionMapper mapper) {
        this.webClient = webClient;
        this.token = token;
        this.enabled = enabled;
        this.cacheTimeout = cacheTimeout;
        this.mapper = mapper;

        log.info("TugdkGpsDataProvider initialized: enabled={}, cacheTimeout={}s",
                enabled, cacheTimeout.toSeconds());
    }

    @Override
    public GpsProviderType getProviderType() {
        return GpsProviderType.TUGDK;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public int getPriority() {
        return 20;
    }

    @Override
    public Mono<List<GpsPositionDTO>> fetchPositionsByDeviceIds(List<String> deviceIds) {
        if (!enabled) {
            log.debug("[TUGDK] Provider is disabled, returning empty list");
            return Mono.just(List.of());
        }

        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        Set<String> deviceIdSet = Set.copyOf(deviceIds);

        log.debug("[TUGDK] Fetching positions for {} devices", deviceIds.size());

        return fetchAllPositions()
                .map(allPositions -> {
                    List<GpsPositionDTO> filtered = allPositions.stream()
                            .filter(pos -> pos.getDeviceId() != null && deviceIdSet.contains(pos.getDeviceId()))
                            .toList();

                    log.debug("[TUGDK] Filtered {} positions from {} total for {} requested devices",
                            filtered.size(), allPositions.size(), deviceIds.size());

                    return filtered;
                });
    }

    @Override
    public Mono<List<GpsPositionDTO>> fetchAllPositions() {
        if (!enabled) {
            log.debug("[TUGDK] Provider is disabled, returning empty list");
            return Mono.just(List.of());
        }

        if (isCacheValid()) {
            log.debug("[TUGDK] Returning {} cached positions", cachedPositions.size());
            return Mono.just(cachedPositions);
        }

        log.debug("[TUGDK] Fetching all positions from API");

        return webClient.get()
                .uri("/api/positions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<TugdkGpsResponseDTO>>() {})
                .map(responses -> {
                    List<GpsPositionDTO> positions = mapper.mapAll(responses);

                    cachedPositions = positions;
                    cacheTimestamp = System.currentTimeMillis();

                    log.info("[TUGDK] Fetched {} positions ({} valid) from API",
                            responses.size(), positions.size());

                    return positions;
                })
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(1))
                        .maxBackoff(Duration.ofSeconds(5))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.warn("[TUGDK] Retrying API request, attempt {}/3",
                                        retrySignal.totalRetries() + 1)))
                .onErrorResume(error -> {
                    log.error("[TUGDK] Failed to fetch positions: {} - {}",
                            error.getClass().getSimpleName(), error.getMessage());

                    if (!cachedPositions.isEmpty()) {
                        log.info("[TUGDK] Returning {} stale cached positions due to error", cachedPositions.size());
                        return Mono.just(cachedPositions);
                    }

                    return Mono.just(List.of());
                });
    }

    @Override
    public Mono<Boolean> healthCheck() {
        if (!enabled) {
            return Mono.just(false);
        }

        log.debug("[TUGDK] Performing health check");

        return webClient.get()
                .uri("/api/positions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("[TUGDK] Health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }

    private boolean isCacheValid() {
        if (cachedPositions.isEmpty()) {
            return false;
        }
        long age = System.currentTimeMillis() - cacheTimestamp;
        return age < cacheTimeout.toMillis();
    }

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            if (ex.getStatusCode().is4xxClientError() && ex.getStatusCode().value() != 429) {
                return false;
            }
        }
        return true;
    }
}
