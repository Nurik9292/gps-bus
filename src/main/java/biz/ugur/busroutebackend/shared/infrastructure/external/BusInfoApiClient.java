package biz.ugur.busroutebackend.shared.infrastructure.external;

import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@Component("busInfoApiClientComponent")
@Slf4j
public class BusInfoApiClient {

    private final WebClient webClient;
    private final String basicAuthHeader;
    private final ObjectMapper objectMapper;

    public BusInfoApiClient(@Qualifier("busInfoApiClient") WebClient webClient,
                            @Value("${external.api.bus-info.username}") String username,
                            @Value("${external.api.bus-info.password}") String password,
                            ObjectMapper objectMapper) {
        this.webClient = webClient;
        this.objectMapper = objectMapper;
        this.basicAuthHeader = createBasicAuthHeader(username, password);
    }


    public Mono<List<BusInfoDTO>> fetchAllBusInfo() {
        log.debug("Fetching bus route assignments from external API");

        return webClient.get()
                .uri("/gps/buses/info")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .exchangeToMono(response -> {
                    HttpStatusCode statusCode = response.statusCode();
                    HttpStatus status = HttpStatus.resolve(statusCode.value());

                    if (status == HttpStatus.UNAUTHORIZED) {
                        log.error("Bus Info API authentication failed");
                        return Mono.error(new BusInfoApiException("Bus Info API authentication failed"));
                    }

                    if (status != null && status.is4xxClientError()) {
                        return response.bodyToMono(String.class)
                                .defaultIfEmpty("No response body")
                                .flatMap(body -> {
                                    log.error("Bus Info API client error {}: {}", status, body);
                                    return Mono.error(new BusInfoApiException("Bus Info API client error: " + body));
                                });
                    }

                    if (status != null && status.is5xxServerError()) {
                        log.error("Bus Info API server error: {}", status);
                        return Mono.error(new BusInfoApiException("Bus Info API server error"));
                    }

                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                        try {

                            List<BusInfoDTO> list = Arrays.asList(
                                    objectMapper.readValue(body, BusInfoDTO[].class)
                            );

                            return Mono.just(list);
                        } catch (Exception e) {
                            return Mono.error(new BusInfoApiException("Failed to parse BusInfoDTO JSON", e));
                        }
                    });
                    
                })
                .timeout(Duration.ofSeconds(30))
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                        .doBeforeRetry(retrySignal ->
                                log.warn("Retrying Bus Info API call, attempt: {}", retrySignal.totalRetries() + 1)))
                .doOnSuccess(busInfos -> log.info("Successfully fetched {} bus route assignments", busInfos.size()))
                .doOnError(error -> log.error("Failed to fetch bus route assignments", error))
                .onErrorMap(this::mapToBusInfoApiException);
    }



    public Mono<BusInfoDTO> fetchBusInfo(String licensePlate) {
        log.debug("Fetching bus info for license plate: {}", licensePlate);

        return fetchAllBusInfo()
                .flatMapMany(Flux::fromIterable)
                .filter(busInfo -> licensePlate.equals(busInfo.getCarNumber()))
                .next()
                .doOnNext(busInfo -> log.debug("Found bus info for {}: route {}",
                        licensePlate, busInfo.getRouteNumber()))
                .switchIfEmpty(Mono.error(new BusInfoApiException("Bus info not found: " + licensePlate)));
    }


    public Mono<Boolean> healthCheck() {
        log.debug("Performing Bus Info API health check");

        return webClient.get()
                .uri("/gps/buses/info")
                .header(HttpHeaders.AUTHORIZATION, basicAuthHeader)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(10))
                .map(response -> response.getStatusCode().is2xxSuccessful())
                .doOnNext(healthy -> log.info("Bus Info API health check: {}", healthy ? "OK" : "FAILED"))
                .onErrorReturn(false);
    }


    private String createBasicAuthHeader(String username, String password) {
        String credentials = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        return "Basic " + encoded;
    }

    private BusInfoApiException mapToBusInfoApiException(Throwable throwable) {
        if (throwable instanceof BusInfoApiException) {
            return (BusInfoApiException) throwable;
        }

        String message = "Bus Info API integration error: " + throwable.getMessage();
        return new BusInfoApiException(message, throwable);
    }

    public static class BusInfoApiException extends RuntimeException {
        public BusInfoApiException(String message) {
            super(message);
        }

        public BusInfoApiException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}