package biz.ugur.busroutebackend.transport.application.service;

import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.GpsDataProvider;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@Slf4j
public class GpsDataAggregatorService {

    private final Map<GpsProviderType, GpsDataProvider> providersByType;
    @Getter
    private final List<GpsDataProvider> enabledProviders;

    public GpsDataAggregatorService(List<GpsDataProvider> providers) {
        this.providersByType = providers.stream()
                .collect(Collectors.toMap(
                        GpsDataProvider::getProviderType,
                        provider -> provider,
                        (existing, replacement) -> {
                            log.warn("Duplicate GPS provider for type {}, using first one",
                                    existing.getProviderType());
                            return existing;
                        }
                ));

        this.enabledProviders = providers.stream()
                .filter(GpsDataProvider::isEnabled)
                .sorted(Comparator.comparingInt(GpsDataProvider::getPriority))
                .toList();

        log.info("GpsDataAggregatorService initialized with {} providers ({} enabled): {}",
                providers.size(),
                enabledProviders.size(),
                enabledProviders.stream()
                        .map(p -> p.getProviderType().getCode())
                        .collect(Collectors.joining(", ")));
    }


    public Mono<List<GpsPositionDTO>> fetchPositionsByProvider(
            GpsProviderType providerType,
            List<String> deviceIds) {

        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        GpsDataProvider provider = providersByType.get(providerType);

        if (provider == null) {
            log.warn("No GPS provider registered for type {}", providerType);
            return Mono.just(List.of());
        }

        if (!provider.isEnabled()) {
            log.debug("GPS provider {} is disabled, returning empty list", providerType);
            return Mono.just(List.of());
        }

        log.debug("Fetching {} positions from provider {}", deviceIds.size(), providerType);

        return provider.fetchPositionsByDeviceIds(deviceIds)
                .doOnSuccess(positions ->
                        log.debug("Provider {} returned {} positions for {} devices",
                                providerType, positions.size(), deviceIds.size()))
                .onErrorResume(error -> {
                    log.error("Provider {} failed to fetch positions — returning empty list. Error: {}",
                            providerType, error.getMessage());
                    return Mono.just(List.of());
                });
    }


    public Mono<List<GpsPositionDTO>> fetchPositionsGroupedByProvider(
            Map<GpsProviderType, List<String>> devicesByProvider) {

        if (devicesByProvider == null || devicesByProvider.isEmpty()) {
            return Mono.just(List.of());
        }

        log.debug("Fetching positions from {} providers", devicesByProvider.size());

        return Flux.fromIterable(devicesByProvider.entrySet())
                .flatMap(entry -> fetchPositionsByProvider(entry.getKey(), entry.getValue()))
                .flatMapIterable(list -> list)
                .collectList()
                .doOnSuccess(positions ->
                        log.debug("Aggregated {} GPS positions from {} providers",
                                positions.size(), devicesByProvider.size()));
    }


    public Mono<List<GpsPositionDTO>> fetchAllPositionsFromAllProviders() {
        if (enabledProviders.isEmpty()) {
            log.warn("No enabled GPS providers available");
            return Mono.just(List.of());
        }

        log.debug("Fetching all positions from {} enabled providers", enabledProviders.size());

        return Flux.fromIterable(enabledProviders)
                .flatMap(provider -> provider.fetchAllPositions()
                        .doOnNext(list -> {
                            log.info("[GPS_PIPELINE] GPS_API_RECV provider={} count={}",
                                    provider.getProviderType().getCode(), list.size());
                            java.util.Map<String, Long> deviceIdCounts = list.stream()
                                    .filter(p -> p.getDeviceId() != null)
                                    .collect(java.util.stream.Collectors.groupingBy(
                                            GpsPositionDTO::getDeviceId,
                                            java.util.stream.Collectors.counting()));
                            long distinctDevices = deviceIdCounts.size();
                            long duplicatesInFetch = list.size() - distinctDevices;
                            if (duplicatesInFetch > 0) {
                                log.info("[GPS_PIPELINE] GPS_API_DUPES_IN_FETCH provider={} total={} distinct_devices={} duplicates={}",
                                        provider.getProviderType().getCode(), list.size(),
                                        distinctDevices, duplicatesInFetch);
                            }
                        })
                        .onErrorResume(error -> {
                            log.error("Provider {} failed: {}", provider.getProviderType(), error.getMessage());
                            return Mono.just(List.of());
                        }))
                .flatMapIterable(list -> list)
                .collectList()
                .doOnSuccess(positions -> {
                    java.util.Map<GpsProviderType, Long> byProvider = positions.stream()
                            .filter(p -> p.getGpsProvider() != null)
                            .collect(java.util.stream.Collectors.groupingBy(
                                    GpsPositionDTO::getGpsProvider,
                                    java.util.stream.Collectors.counting()));
                    java.util.Set<String> devicesFromChina = positions.stream()
                            .filter(p -> p.getGpsProvider() == GpsProviderType.CHINA && p.getDeviceId() != null)
                            .map(GpsPositionDTO::getDeviceId)
                            .collect(java.util.stream.Collectors.toSet());
                    java.util.Set<String> devicesFromTugdk = positions.stream()
                            .filter(p -> p.getGpsProvider() == GpsProviderType.TUGDK && p.getDeviceId() != null)
                            .map(GpsPositionDTO::getDeviceId)
                            .collect(java.util.stream.Collectors.toSet());
                    java.util.Set<String> crossProviderDevices = new java.util.HashSet<>(devicesFromChina);
                    crossProviderDevices.retainAll(devicesFromTugdk);
                    log.info("[GPS_PIPELINE] GPS_AGGREGATE total={} by_provider={} cross_provider_devices={}",
                            positions.size(), byProvider, crossProviderDevices.size());
                    if (!crossProviderDevices.isEmpty()) {
                        log.warn("[GPS_PIPELINE] GPS_CROSS_PROVIDER devices reporting to BOTH providers: {} (sample: {})",
                                crossProviderDevices.size(),
                                crossProviderDevices.stream().limit(5).toList());
                    }
                });
    }


    public Optional<GpsDataProvider> getProvider(GpsProviderType type) {
        return Optional.ofNullable(providersByType.get(type));
    }



    public boolean isProviderEnabled(GpsProviderType type) {
        GpsDataProvider provider = providersByType.get(type);
        return provider != null && provider.isEnabled();
    }


    public Mono<Map<GpsProviderType, Boolean>> healthCheckAll() {
        return Flux.fromIterable(enabledProviders)
                .flatMap(provider -> provider.healthCheck()
                        .map(healthy -> Map.entry(provider.getProviderType(), healthy))
                        .onErrorResume(error -> {
                            log.error("Health check failed for {}: {}",
                                    provider.getProviderType(), error.getMessage());
                            return Mono.just(Map.entry(provider.getProviderType(), false));
                        }))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .doOnSuccess(results ->
                        log.debug("Health check results: {}", results));
    }


    public int getProviderCount() {
        return providersByType.size();
    }


    public int getEnabledProviderCount() {
        return enabledProviders.size();
    }
}
