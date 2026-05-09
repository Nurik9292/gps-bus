package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.TugdkTenantsProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "external.api.gps-tugdk", name = "enabled", havingValue = "true")
public class VehicleTenantResolver {

    private static final String SQL_LOAD_DEVICE_TENANTS = """
            SELECT v.device_id AS device_id, c.gps_tenant_id AS tenant_id
              FROM vehicles v
              JOIN cities c ON c.id = v.city_id
             WHERE v.is_active = true
               AND v.device_id IS NOT NULL
               AND c.gps_tenant_id IS NOT NULL
            """;

    private final DatabaseClient databaseClient;
    private final String defaultTenantId;
    private final AtomicReference<Map<String, String>> deviceToTenant =
            new AtomicReference<>(Map.of());

    public VehicleTenantResolver(DatabaseClient databaseClient,
                                  @Value("${external.api.gps-tugdk.default-tenant-id:" + TugdkTenantsProperties.DEFAULT_TENANT_ID + "}")
                                  String defaultTenantId) {
        this.databaseClient = databaseClient;
        this.defaultTenantId = defaultTenantId;
    }

    @PostConstruct
    void init() {
        refreshNow();
    }

    @Scheduled(fixedDelayString = "${external.api.gps-tugdk.tenant-cache-refresh-ms:60000}")
    public void scheduledRefresh() {
        refreshNow();
    }

    public void refreshNow() {
        databaseClient.sql(SQL_LOAD_DEVICE_TENANTS)
                .map((row, meta) -> Map.entry(
                        row.get("device_id", String.class),
                        row.get("tenant_id", String.class)))
                .all()
                .filter(e -> e.getKey() != null && e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, ConcurrentHashMap::new))
                .doOnNext(loaded -> {
                    deviceToTenant.set(Map.copyOf(loaded));
                    log.info("[GPS_TENANT_RESOLVER] refreshed deviceId->tenant cache: {} entries", loaded.size());
                })
                .doOnError(err ->
                        log.error("[GPS_TENANT_RESOLVER] refresh failed: {}", err.getMessage()))
                .onErrorResume(err -> reactor.core.publisher.Mono.empty())
                .block();
    }

    public String resolveTenantId(String deviceId) {
        if (deviceId == null) {
            return defaultTenantId;
        }
        return deviceToTenant.get().getOrDefault(deviceId, defaultTenantId);
    }

    public Map<String, List<String>> groupByTenant(List<String> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }
        return deviceIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.groupingBy(this::resolveTenantId));
    }

    public int cachedEntryCount() {
        return deviceToTenant.get().size();
    }
}
