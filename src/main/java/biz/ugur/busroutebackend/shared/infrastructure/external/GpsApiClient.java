package biz.ugur.busroutebackend.shared.infrastructure.external;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.List;

@Component("gpsApiClientComponent")
@Slf4j
public class GpsApiClient {

    private final WebClient webClient;
    private final String bearerToken;
    private final ObjectMapper objectMapper;

    public GpsApiClient(@Qualifier("gpsApiClient") WebClient webClient,
                        @Value("${external.api.gps.token}") String bearerToken,
                        ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.bearerToken = bearerToken;
        this.objectMapper = objectMapper;
    }


    public Mono<List<GpsPositionDTO>> fetchAllVehiclePositions() {
        log.debug("Fetching GPS positions from external API");

        return webClient.get()
                .uri("/api/positions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .exchangeToMono(response -> {
                    HttpStatusCode statusCode = response.statusCode();
                    HttpStatus status = HttpStatus.resolve(statusCode.value());

                    if (status == HttpStatus.UNAUTHORIZED) {
                        log.error("GPS API authentication failed - invalid token");
                        return Mono.error(new GpsApiException("GPS API authentication failed"));
                    }

                    if (status != null && status.is4xxClientError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("No response body")
                                .flatMap(body -> {
                                    log.error("GPS API client error {}: {}", status, body);
                                    return Mono.error(new GpsApiException("GPS API client error: " + body));
                                });
                    }

                    if (status != null && status.is5xxServerError()) {
                        log.error("GPS API server error: {}", status);
                        return Mono.error(new GpsApiException("GPS API server error"));
                    }

                    return response.bodyToFlux(GpsPositionDTO.class)
                            .collectList();
                })
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .filter(this::isRetryableException)
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying GPS API call, attempt: {}", retrySignal.totalRetries() + 1)))
                .doOnSuccess(positions -> log.info("Successfully fetched {} GPS positions", positions.size()))
                .doOnError(error -> log.error("Failed to fetch GPS positions", error))
                .onErrorMap(this::mapToGpsApiException);
    }



    public Mono<GpsPositionDTO> fetchVehiclePosition(String deviceId) {
        log.debug("Fetching GPS position for device: {}", deviceId);

        return fetchAllVehiclePositions()
                .flatMapMany(Flux::fromIterable)
                .filter(position -> deviceId.equals(position.getDeviceId()))
                .next()
                .doOnNext(position -> log.debug("Found position for device {}: ({}, {})",
                        deviceId, position.getLatitude(), position.getLongitude()))
                .switchIfEmpty(Mono.error(new GpsApiException("Vehicle not found: " + deviceId)));
    }

    /**
     * Проверка доступности GPS API
     */
    public Mono<Boolean> healthCheck() {
        log.debug("Performing GPS API health check");

        return webClient.get()
                .uri("/api/positions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("GPS API health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }

    // Приватные методы

    private boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof WebClientResponseException) {
            WebClientResponseException ex = (WebClientResponseException) throwable;
            // Не повторяем при ошибках аутентификации и клиентских ошибках
            return !ex.getStatusCode().is4xxClientError();
        }
        // Повторяем при timeout, connection errors и других сетевых проблемах
        return true;
    }

    private GpsApiException mapToGpsApiException(Throwable throwable) {
        if (throwable instanceof GpsApiException) {
            return (GpsApiException) throwable;
        }

        String message = "GPS API integration error: " + throwable.getMessage();
        return new GpsApiException(message, throwable);
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