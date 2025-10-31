package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.shared.infrastructure.external.BusInfoApiClient;
import biz.ugur.busroutebackend.shared.infrastructure.external.GpsApiClient;
import biz.ugur.busroutebackend.transport.application.dto.BusInfoDTO;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.application.services.ExternalApiService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;


@Service
@Slf4j
public class ResilientExternalApiServiceImpl implements ExternalApiService {

    private final GpsApiClient gpsApiClient;
    private final BusInfoApiClient busInfoApiClient;

    public ResilientExternalApiServiceImpl(GpsApiClient gpsApiClient, BusInfoApiClient busInfoApiClient) {
        this.gpsApiClient = gpsApiClient;
        this.busInfoApiClient = busInfoApiClient;
    }

    @Override
    @CircuitBreaker(name = "gpsApi", fallbackMethod = "fallbackFetchGpsPositions")
    @RateLimiter(name = "gpsApi")
    @Bulkhead(name = "gpsApi")
    @TimeLimiter(name = "gpsApi")
    public Mono<List<GpsPositionDTO>> fetchAllVehiclePositions() {
        log.debug("Fetching GPS positions with Resilience4j protection");
        return gpsApiClient.fetchAllVehiclePositions();
    }

    @Override
    @CircuitBreaker(name = "busInfoApi", fallbackMethod = "fallbackFetchBusInfo")
    @RateLimiter(name = "busInfoApi")
    @Bulkhead(name = "busInfoApi")
    @TimeLimiter(name = "busInfoApi")
    public Mono<List<BusInfoDTO>> fetchAllBusInfo() {
        log.debug("Fetching bus info with Resilience4j protection");
        return busInfoApiClient.fetchAllBusInfo();
    }

    @Override
    @CircuitBreaker(name = "gpsApi", fallbackMethod = "fallbackHealthCheck")
    @RateLimiter(name = "gpsApi")
    public Mono<Boolean> gpsHealthCheck() {
        return gpsApiClient.healthCheck();
    }

    @Override
    @CircuitBreaker(name = "busInfoApi", fallbackMethod = "fallbackHealthCheck")
    @RateLimiter(name = "busInfoApi")
    public Mono<Boolean> busInfoHealthCheck() {
        return busInfoApiClient.healthCheck();
    }

    // ===== Fallback Methods =====

    private Mono<List<GpsPositionDTO>> fallbackFetchGpsPositions(Exception ex) {
        log.warn("GPS API fallback triggered: {}. Returning empty list", ex.getMessage());
        return Mono.just(List.of());
    }

    private Mono<List<BusInfoDTO>> fallbackFetchBusInfo(Exception ex) {
        log.warn("BusInfo API fallback triggered: {}. Returning empty list", ex.getMessage());
        return Mono.just(List.of());
    }

    private Mono<Boolean> fallbackHealthCheck(Exception ex) {
        log.warn("Health check fallback triggered: {}. Returning false", ex.getMessage());
        return Mono.just(false);
    }
}
