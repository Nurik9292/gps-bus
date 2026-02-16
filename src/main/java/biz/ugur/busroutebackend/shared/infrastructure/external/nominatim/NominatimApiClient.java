package biz.ugur.busroutebackend.shared.infrastructure.external.nominatim;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "external.api.nominatim", name = "enabled", havingValue = "true")
@Slf4j
public class NominatimApiClient {

    private final WebClient webClient;

    public NominatimApiClient(@Qualifier("nominatimClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @CircuitBreaker(name = "nominatimApi", fallbackMethod = "searchFallback")
    public Mono<List<NominatimPlace>> search(String query, String countryCode, int limit) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/search")
                        .queryParam("q", query)
                        .queryParam("format", "jsonv2")
                        .queryParam("addressdetails", 1)
                        .queryParam("countrycodes", countryCode)
                        .queryParam("limit", limit)
                        .queryParam("accept-language", "ru,en")
                        .build())
                .retrieve()
                .bodyToFlux(NominatimPlace.class)
                .collectList()
                .timeout(Duration.ofSeconds(10))
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1)).maxBackoff(Duration.ofSeconds(3)))
                .doOnSuccess(results -> log.debug("Nominatim search '{}': {} results", query, results.size()))
                .onErrorResume(e -> {
                    log.error("Nominatim search failed for query '{}': {}", query, e.getMessage());
                    return Mono.just(List.of());
                });
    }

    @CircuitBreaker(name = "nominatimApi", fallbackMethod = "reverseFallback")
    public Mono<NominatimPlace> reverse(double lat, double lon) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder.path("/reverse")
                        .queryParam("lat", lat)
                        .queryParam("lon", lon)
                        .queryParam("format", "jsonv2")
                        .queryParam("addressdetails", 1)
                        .queryParam("accept-language", "ru,en")
                        .build())
                .retrieve()
                .bodyToMono(NominatimPlace.class)
                .timeout(Duration.ofSeconds(5))
                .doOnSuccess(result -> log.debug("Nominatim reverse ({}, {}): {}", lat, lon,
                        result != null ? result.displayName() : "empty"))
                .onErrorResume(e -> {
                    log.error("Nominatim reverse failed for ({}, {}): {}", lat, lon, e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unused")
    private Mono<List<NominatimPlace>> searchFallback(String query, String countryCode, int limit, Throwable t) {
        log.warn("Nominatim search circuit breaker fallback for '{}': {}", query, t.getMessage());
        return Mono.just(List.of());
    }

    @SuppressWarnings("unused")
    private Mono<NominatimPlace> reverseFallback(double lat, double lon, Throwable t) {
        log.warn("Nominatim reverse circuit breaker fallback for ({}, {}): {}", lat, lon, t.getMessage());
        return Mono.empty();
    }
}
