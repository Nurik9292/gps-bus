package biz.ugur.busroutebackend.shared.infrastructure.external.osrm;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "external.api.osrm", name = "enabled", havingValue = "true")
@Slf4j
public class OsrmApiClient {

    private final WebClient webClient;

    public OsrmApiClient(@Qualifier("osrmClient") WebClient webClient) {
        this.webClient = webClient;
    }

    @CircuitBreaker(name = "osrmApi", fallbackMethod = "routeFallback")
    public Mono<OsrmRouteResponse> getRoute(double fromLon, double fromLat, double toLon, double toLat) {
        String coordinates = String.format("%.6f,%.6f;%.6f,%.6f", fromLon, fromLat, toLon, toLat);

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/route/v1/foot/" + coordinates)
                        .queryParam("overview", "full")
                        .queryParam("geometries", "geojson")
                        .build())
                .retrieve()
                .bodyToMono(OsrmRouteResponse.class)
                .timeout(Duration.ofSeconds(3))
                .doOnSuccess(r -> {
                    if (r != null && !r.routes().isEmpty()) {
                        log.debug("OSRM route ({},{}) -> ({},{}): {}m",
                                fromLat, fromLon, toLat, toLon, (int) r.routes().get(0).distance());
                    }
                })
                .onErrorResume(e -> {
                    log.warn("OSRM request failed ({},{}) -> ({},{}): {}",
                            fromLat, fromLon, toLat, toLon, e.getMessage());
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unused")
    private Mono<OsrmRouteResponse> routeFallback(double fromLon, double fromLat,
                                                   double toLon, double toLat, Throwable t) {
        log.warn("OSRM circuit breaker open: {}", t.getMessage());
        return Mono.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OsrmRouteResponse(
            @JsonProperty("code") String code,
            @JsonProperty("routes") List<OsrmRoute> routes
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OsrmRoute(
            @JsonProperty("geometry") OsrmGeometry geometry,
            @JsonProperty("distance") double distance,
            @JsonProperty("duration") double duration
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OsrmGeometry(
            @JsonProperty("type") String type,
            @JsonProperty("coordinates") List<List<Double>> coordinates
    ) {}
}
