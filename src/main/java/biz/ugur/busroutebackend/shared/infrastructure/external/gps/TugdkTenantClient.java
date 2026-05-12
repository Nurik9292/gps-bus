package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto.TugdkGpsResponseDTO;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.mapper.TugdkGpsPositionMapper;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.FetchOutcome;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.GpsProviderHealthMonitor;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class TugdkTenantClient {

    private static final Duration MAX_FALLBACK_CACHE_AGE = Duration.ofMinutes(2);
    private static final long FRESH_THRESHOLD_SEC = 5 * 60;

    private final String tenantId;
    private final WebClient webClient;
    private final String token;
    private final TugdkGpsPositionMapper mapper;
    private final GpsProviderProperties commonProperties;
    private final Duration cacheTimeout;
    private final Optional<GpsProviderHealthMonitor> healthMonitor;

    private final AtomicReference<CachedPositions> cache = new AtomicReference<>(CachedPositions.empty());

    public TugdkTenantClient(String tenantId,
                              WebClient webClient,
                              String token,
                              TugdkGpsPositionMapper mapper,
                              GpsProviderProperties commonProperties,
                              Duration cacheTimeout,
                              Optional<GpsProviderHealthMonitor> healthMonitor) {
        this.tenantId = tenantId;
        this.webClient = webClient;
        this.token = token;
        this.mapper = mapper;
        this.commonProperties = commonProperties;
        this.cacheTimeout = cacheTimeout;
        this.healthMonitor = healthMonitor;
    }

    public String tenantId() {
        return tenantId;
    }

    public Mono<List<GpsPositionDTO>> fetchAllPositions() {
        CachedPositions cached = cache.get();
        if (cached.isValid(cacheTimeout)) {
            log.debug("[TUGDK:{}] cache hit age={}s positions={}",
                    tenantId, cached.ageSeconds(), cached.positions().size());
            return Mono.just(cached.positions());
        }

        return webClient.get()
                .uri("/api/positions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<TugdkGpsResponseDTO>>() {})
                .map(this::cacheAndMap)
                .doOnNext(positions -> healthMonitor.ifPresent(m -> {
                    String key = "TUGDK:" + tenantId;
                    if (positions.isEmpty()) {
                        m.recordFetch(key, new FetchOutcome.Empty());
                    } else {
                        m.recordFetch(key, new FetchOutcome.Success(
                                positions.size(), countFresh(positions), latestFixTime(positions)));
                    }
                }))
                .doOnError(err -> healthMonitor.ifPresent(m ->
                        m.recordError("TUGDK:" + tenantId, err)))
                .timeout(commonProperties.getTimeout().getRequest())
                .retryWhen(retrySpec())
                .onErrorResume(this::handleError);
    }

    public Mono<GpsPositionDTO> fetchSingleDevicePosition(String deviceId) {
        return webClient.get()
                .uri("/api/positions/{deviceId}", deviceId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(TugdkGpsResponseDTO.class)
                .map(mapper::map)
                .timeout(commonProperties.getTimeout().getRequest())
                .onErrorResume(error -> {
                    log.debug("[TUGDK:{}] failed device {} fetch: {}",
                            tenantId, deviceId, error.getMessage());
                    return Mono.empty();
                });
    }

    public Mono<Boolean> healthCheck() {
        return webClient.get()
                .uri("/api/positions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .toBodilessEntity()
                .timeout(commonProperties.getTimeout().getHealthCheck())
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("[TUGDK:{}] health check: {}",
                        tenantId, healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }

    public CachedPositions cachedSnapshot() {
        return cache.get();
    }

    public boolean cacheHasUsableFallback() {
        CachedPositions cached = cache.get();
        return cached.hasData() && !cached.isTooStale(MAX_FALLBACK_CACHE_AGE);
    }

    public Mono<List<GpsPositionDTO>> emptyFetch() {
        return Mono.just(List.of());
    }

    private List<GpsPositionDTO> cacheAndMap(List<TugdkGpsResponseDTO> raw) {
        List<GpsPositionDTO> mapped = mapper.mapAll(raw);
        cache.set(CachedPositions.of(mapped));
        log.info("[TUGDK:{}] fetched {} positions ({} valid) from API",
                tenantId, raw.size(), mapped.size());
        return mapped;
    }

    private Retry retrySpec() {
        return Retry.backoff(
                        commonProperties.getRetry().getMaxAttempts(),
                        commonProperties.getRetry().getInitialInterval())
                .maxBackoff(commonProperties.getRetry().getMaxInterval())
                .multiplier(commonProperties.getRetry().getMultiplier())
                .filter(this::isRetryable)
                .doBeforeRetry(retrySignal ->
                        log.warn("[TUGDK:{}] retry attempt {}/{}",
                                tenantId,
                                retrySignal.totalRetries() + 1,
                                commonProperties.getRetry().getMaxAttempts()));
    }

    private boolean isRetryable(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 429) {
                return true;
            }
            return !ex.getStatusCode().is4xxClientError();
        }
        return true;
    }

    private int countFresh(List<GpsPositionDTO> positions) {
        Instant cutoff = Instant.now().minusSeconds(FRESH_THRESHOLD_SEC);
        int fresh = 0;
        for (var p : positions) {
            if (p.getFixTime() != null
                    && p.getFixTime().toInstant(ZoneOffset.UTC).isAfter(cutoff)) {
                fresh++;
            }
        }
        return fresh;
    }

    private Instant latestFixTime(List<GpsPositionDTO> positions) {
        Instant latest = null;
        for (var p : positions) {
            if (p.getFixTime() == null) continue;
            Instant t = p.getFixTime().toInstant(ZoneOffset.UTC);
            if (latest == null || t.isAfter(latest)) latest = t;
        }
        return latest;
    }

    private Mono<List<GpsPositionDTO>> handleError(Throwable error) {
        log.error("[TUGDK:{}] fetch error {}: {}",
                tenantId, error.getClass().getSimpleName(), error.getMessage());
        if (cacheHasUsableFallback()) {
            CachedPositions cached = cache.get();
            log.info("[TUGDK:{}] returning {} cached positions (age={}s) from error fallback",
                    tenantId, cached.positions().size(), cached.ageSeconds());
            return Mono.just(cached.positions());
        }
        return Mono.just(List.of());
    }

    public record CachedPositions(List<GpsPositionDTO> positions, long timestamp) {

        public static CachedPositions empty() {
            return new CachedPositions(List.of(), 0);
        }

        public static CachedPositions of(List<GpsPositionDTO> positions) {
            return new CachedPositions(positions, System.currentTimeMillis());
        }

        public boolean hasData() {
            return !positions.isEmpty();
        }

        public long ageMs() {
            return System.currentTimeMillis() - timestamp;
        }

        public long ageSeconds() {
            return ageMs() / 1000;
        }

        public boolean isValid(Duration timeout) {
            if (positions.isEmpty()) return false;
            return ageMs() < timeout.toMillis();
        }

        public boolean isTooStale(Duration maxAge) {
            return ageMs() >= maxAge.toMillis();
        }
    }
}
