package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.GpsDataProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.util.List;


@Slf4j
public abstract class AbstractGpsDataProvider implements GpsDataProvider {

    protected final WebClient webClient;
    protected final GpsProviderProperties properties;
    protected final boolean enabled;

    protected AbstractGpsDataProvider(WebClient webClient,
                                      GpsProviderProperties properties,
                                      boolean enabled) {
        this.webClient = webClient;
        this.properties = properties;
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    protected Retry createRetrySpec() {
        return Retry.backoff(
                        properties.getRetry().getMaxAttempts(),
                        properties.getRetry().getInitialInterval())
                .maxBackoff(properties.getRetry().getMaxInterval())
                .multiplier(properties.getRetry().getMultiplier())
                .filter(this::isRetryableException)
                .doBeforeRetry(retrySignal ->
                        log.warn("[{}] Retrying API request, attempt {}/{}",
                                getProviderType().getCode(),
                                retrySignal.totalRetries() + 1,
                                properties.getRetry().getMaxAttempts()));
    }

    protected boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException ex) {
            int statusCode = ex.getStatusCode().value();
            if (statusCode == 429) {
                return true;
            }
            return !ex.getStatusCode().is4xxClientError();
        }
        return true;
    }

    protected Mono<List<GpsPositionDTO>> handleDisabled() {
        log.debug("[{}] Provider is disabled, returning empty list", getProviderType().getCode());
        return Mono.just(List.of());
    }

    protected Mono<List<GpsPositionDTO>> handleEmptyRequest() {
        return Mono.just(List.of());
    }

    protected Mono<List<GpsPositionDTO>> handleError(Throwable error, int requestedCount) {
        log.error("[{}] Failed to fetch GPS positions for {} devices: {}",
                getProviderType().getCode(), requestedCount, error.getMessage());
        return Mono.just(List.of());
    }

    protected void logSuccess(int positionsCount, int requestedCount) {
        log.info("[{}] Successfully fetched {} GPS positions for {} requested devices",
                getProviderType().getCode(), positionsCount, requestedCount);
    }

    protected void logFetch(int deviceCount) {
        log.debug("[{}] Fetching positions for {} devices", getProviderType().getCode(), deviceCount);
    }
}
