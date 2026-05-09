package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsFetchOptimizationProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.TugdkTenantsProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.metrics.GpsFetchStrategyMetricsRecorder;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.service.GpsDataProvider;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "external.api.gps-tugdk", name = "enabled", havingValue = "true")
public class TugdkGpsDataProvider implements GpsDataProvider {

    private final TugdkTenantsProperties tenantsProperties;
    private final GpsProviderProperties commonProperties;
    private final GpsFetchOptimizationProperties optimizationProperties;
    private final GpsFetchStrategyMetricsRecorder metricsRecorder;
    private final VehicleTenantResolver tenantResolver;
    private final Map<String, TugdkTenantClient> tenantClients;

    public TugdkGpsDataProvider(TugdkTenantsProperties tenantsProperties,
                                 GpsProviderProperties commonProperties,
                                 GpsFetchOptimizationProperties optimizationProperties,
                                 GpsFetchStrategyMetricsRecorder metricsRecorder,
                                 VehicleTenantResolver tenantResolver,
                                 Map<String, TugdkTenantClient> tenantClients) {
        this.tenantsProperties = tenantsProperties;
        this.commonProperties = commonProperties;
        this.optimizationProperties = optimizationProperties;
        this.metricsRecorder = metricsRecorder;
        this.tenantResolver = tenantResolver;
        this.tenantClients = Map.copyOf(tenantClients);

        log.info("TugdkGpsDataProvider initialized: enabled={} tenants={} cacheTimeout={}s selectiveFetch={} threshold={}",
                tenantsProperties.isEnabled(),
                this.tenantClients.keySet(),
                tenantsProperties.getCacheTimeout().toSeconds(),
                optimizationProperties.isSelectiveFetchEnabled(),
                optimizationProperties.getSelectiveFetchThreshold());
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
    public boolean isEnabled() {
        return tenantsProperties.isEnabled() && !tenantClients.isEmpty();
    }

    @Override
    public Mono<List<GpsPositionDTO>> fetchPositionsByDeviceIds(List<String> deviceIds) {
        if (!isEnabled()) {
            return Mono.just(List.of());
        }
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Mono.just(List.of());
        }

        Map<String, List<String>> byTenant = tenantResolver.groupByTenant(deviceIds);
        log.debug("[TUGDK] grouped {} devices into {} tenants: {}",
                deviceIds.size(), byTenant.size(), byTenant.keySet());

        return Flux.fromIterable(byTenant.entrySet())
                .flatMap(entry -> fetchForTenant(entry.getKey(), entry.getValue()))
                .reduce(new java.util.ArrayList<GpsPositionDTO>(), (acc, list) -> {
                    acc.addAll(list);
                    return acc;
                })
                .map(list -> (List<GpsPositionDTO>) list);
    }

    @Override
    public Mono<List<GpsPositionDTO>> fetchAllPositions() {
        if (!isEnabled()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(tenantClients.values())
                .flatMap(TugdkTenantClient::fetchAllPositions)
                .reduce(new java.util.ArrayList<GpsPositionDTO>(), (acc, list) -> {
                    acc.addAll(list);
                    return acc;
                })
                .map(list -> (List<GpsPositionDTO>) list);
    }

    @Override
    public Mono<Boolean> healthCheck() {
        if (!isEnabled()) {
            return Mono.just(false);
        }
        return Flux.fromIterable(tenantClients.values())
                .flatMap(TugdkTenantClient::healthCheck)
                .all(Boolean::booleanValue);
    }

    private Mono<List<GpsPositionDTO>> fetchForTenant(String tenantId, List<String> deviceIds) {
        TugdkTenantClient client = tenantClients.get(tenantId);
        if (client == null) {
            log.warn("[TUGDK] no tenant client for id={}, dropping {} devices", tenantId, deviceIds.size());
            return Mono.just(List.of());
        }

        Set<String> deviceIdSet = new HashSet<>(deviceIds);
        int deviceCount = deviceIds.size();

        if (shouldUseSelectiveFetch(deviceCount)) {
            metricsRecorder.recordSelectiveFetch(deviceCount, "TUGDK:" + tenantId);
            return Flux.fromIterable(deviceIds)
                    .flatMap(client::fetchSingleDevicePosition,
                            optimizationProperties.getParallelFetchConcurrency())
                    .collectList();
        }

        metricsRecorder.recordBulkFetch(deviceCount, "TUGDK:" + tenantId);
        return client.fetchAllPositions()
                .map(all -> filter(all, deviceIdSet, deviceCount, tenantId));
    }

    private boolean shouldUseSelectiveFetch(int deviceCount) {
        if (!optimizationProperties.isSelectiveFetchEnabled()) {
            return false;
        }
        boolean useSelective = deviceCount <= optimizationProperties.getSelectiveFetchThreshold();
        if (optimizationProperties.isLogStrategyDecisions()) {
            log.info("[TUGDK] selective fetch decision: deviceCount={} threshold={} useSelective={}",
                    deviceCount, optimizationProperties.getSelectiveFetchThreshold(), useSelective);
        }
        return useSelective;
    }

    private List<GpsPositionDTO> filter(List<GpsPositionDTO> all,
                                         Set<String> deviceIdSet,
                                         int requestedCount,
                                         String tenantId) {
        List<GpsPositionDTO> filtered = all.stream()
                .filter(p -> p.getDeviceId() != null && deviceIdSet.contains(p.getDeviceId()))
                .toList();
        log.debug("[TUGDK:{}] filtered {} positions from {} total for {} requested",
                tenantId, filtered.size(), all.size(), requestedCount);
        return filtered;
    }

    Map<String, TugdkTenantClient> tenantClients() {
        return tenantClients;
    }

    static Map<String, TugdkTenantClient> indexById(Collection<TugdkTenantClient> clients) {
        Map<String, TugdkTenantClient> map = new LinkedHashMap<>();
        for (TugdkTenantClient c : clients) {
            map.put(c.tenantId(), c);
        }
        return map;
    }
}
