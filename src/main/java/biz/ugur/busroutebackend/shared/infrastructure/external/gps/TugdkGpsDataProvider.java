package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.dto.TugdkGpsResponseDTO;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.mapper.TugdkGpsPositionMapper;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;


@Component
@Slf4j
@ConditionalOnProperty(prefix = "external.api.gps-tugdk", name = "enabled", havingValue = "true")
public class TugdkGpsDataProvider extends AbstractGpsDataProvider {

    private final String token;
    private final TugdkGpsPositionMapper mapper;
    private final Duration cacheTimeout;

    private final AtomicReference<CachedPositions> cache = new AtomicReference<>(CachedPositions.empty());

    public TugdkGpsDataProvider(
            @Qualifier("tugdkGpsWebClient") WebClient webClient,
            GpsProviderProperties properties,
            @Value("${external.api.gps-tugdk.token}") String token,
            @Value("${external.api.gps-tugdk.enabled:false}") boolean enabled,
            @Value("${external.api.gps-tugdk.cache-timeout:5s}") Duration cacheTimeout,
            TugdkGpsPositionMapper mapper) {
        super(webClient, properties, enabled);
        this.token = token;
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
    public int getPriority() {
        return 20;
    }

    @Override
    @CircuitBreaker(name = "gpsApiTugdk", fallbackMethod = "fetchPositionsByDeviceIdsFallback")
    public Mono<List<GpsPositionDTO>> fetchPositionsByDeviceIds(List<String> deviceIds) {
        if (!enabled) {
            return handleDisabled();
        }

        if (deviceIds == null || deviceIds.isEmpty()) {
            return handleEmptyRequest();
        }

        Set<String> deviceIdSet = Set.copyOf(deviceIds);

        logFetch(deviceIds.size());

        return fetchAllPositions()
                .map(allPositions -> filterPositions(allPositions, deviceIdSet, deviceIds.size()));
    }

    @SuppressWarnings("unused")
    private Mono<List<GpsPositionDTO>> fetchPositionsByDeviceIdsFallback(List<String> deviceIds, Throwable throwable) {
        log.warn("[TUGDK] Circuit breaker fallback for {} devices: {}",
                deviceIds != null ? deviceIds.size() : 0, throwable.getMessage());

        CachedPositions cached = cache.get();
        if (cached.hasData()) {
            Set<String> deviceIdSet = Set.copyOf(deviceIds);
            List<GpsPositionDTO> filtered = filterPositions(cached.positions(), deviceIdSet, deviceIds.size());
            log.info("[TUGDK] Returning {} stale cached positions from fallback", filtered.size());
            return Mono.just(filtered);
        }

        return Mono.just(List.of());
    }

    private List<GpsPositionDTO> filterPositions(List<GpsPositionDTO> allPositions,
                                                  Set<String> deviceIdSet,
                                                  int requestedCount) {
        List<GpsPositionDTO> filtered = allPositions.stream()
                .filter(pos -> pos.getDeviceId() != null && deviceIdSet.contains(pos.getDeviceId()))
                .toList();

        log.debug("[TUGDK] Filtered {} positions from {} total for {} requested devices",
                filtered.size(), allPositions.size(), requestedCount);

        return filtered;
    }

    @Override
    @CircuitBreaker(name = "gpsApiTugdk", fallbackMethod = "fetchAllPositionsFallback")
    public Mono<List<GpsPositionDTO>> fetchAllPositions() {
        if (!enabled) {
            return handleDisabled();
        }

        CachedPositions cached = cache.get();
        if (cached.isValid(cacheTimeout)) {
            log.debug("[TUGDK] Returning {} cached positions", cached.positions().size());
            return Mono.just(cached.positions());
        }

        log.debug("[TUGDK] Fetching all positions from API");

        return webClient.get()
                .uri("/api/positions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .bodyToMono(new ParameterizedTypeReference<List<TugdkGpsResponseDTO>>() {})
                .map(this::processAndCacheResponse)
                .timeout(properties.getTimeout().getRequest())
                .retryWhen(createRetrySpec())
                .onErrorResume(this::handleFetchError);
    }

    @SuppressWarnings("unused")
    private Mono<List<GpsPositionDTO>> fetchAllPositionsFallback(Throwable throwable) {
        log.warn("[TUGDK] Circuit breaker fallback for fetchAllPositions: {}", throwable.getMessage());

        CachedPositions cached = cache.get();
        if (cached.hasData()) {
            log.info("[TUGDK] Returning {} stale cached positions from fallback", cached.positions().size());
            return Mono.just(cached.positions());
        }

        return Mono.just(List.of());
    }

    private List<GpsPositionDTO> processAndCacheResponse(List<TugdkGpsResponseDTO> responses) {
        List<GpsPositionDTO> positions = mapper.mapAll(responses);

        cache.set(CachedPositions.of(positions));

        log.info("[TUGDK] Fetched {} positions ({} valid) from API",
                responses.size(), positions.size());

        return positions;
    }

    private Mono<List<GpsPositionDTO>> handleFetchError(Throwable error) {
        log.error("[TUGDK] Failed to fetch positions: {} - {}",
                error.getClass().getSimpleName(), error.getMessage());

        CachedPositions cached = cache.get();
        if (cached.hasData()) {
            log.info("[TUGDK] Returning {} stale cached positions due to error", cached.positions().size());
            return Mono.just(cached.positions());
        }

        return Mono.just(List.of());
    }

    @Override
    @CircuitBreaker(name = "gpsApiTugdk", fallbackMethod = "healthCheckFallback")
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
                .timeout(properties.getTimeout().getHealthCheck())
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("[TUGDK] Health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }

    @SuppressWarnings("unused")
    private Mono<Boolean> healthCheckFallback(Throwable throwable) {
        log.warn("[TUGDK] Health check circuit breaker fallback: {}", throwable.getMessage());
        return Mono.just(false);
    }

    private record CachedPositions(List<GpsPositionDTO> positions, long timestamp) {

        static CachedPositions empty() {
            return new CachedPositions(List.of(), 0);
        }

        static CachedPositions of(List<GpsPositionDTO> positions) {
            return new CachedPositions(positions, System.currentTimeMillis());
        }

        boolean hasData() {
            return !positions.isEmpty();
        }

        boolean isValid(Duration timeout) {
            if (positions.isEmpty()) {
                return false;
            }
            long age = System.currentTimeMillis() - timestamp;
            return age < timeout.toMillis();
        }
    }
}
