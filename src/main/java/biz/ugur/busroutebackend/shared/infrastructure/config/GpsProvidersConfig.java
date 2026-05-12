package biz.ugur.busroutebackend.shared.infrastructure.config;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.TugdkTenantClient;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.TugdkTenantsProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.mapper.TugdkGpsPositionMapper;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.monitoring.GpsProviderHealthMonitor;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
public class GpsProvidersConfig {

    @Bean
    @ConditionalOnProperty(prefix = "external.api.gps-tugdk", name = "enabled", havingValue = "true")
    public Map<String, TugdkTenantClient> tugdkTenantClients(TugdkTenantsProperties tenantsProperties,
                                                              GpsProviderProperties commonProperties,
                                                              TugdkGpsPositionMapper mapper,
                                                              Optional<GpsProviderHealthMonitor> healthMonitor) {
        List<TugdkTenantsProperties.TenantConfig> effective = tenantsProperties.resolveEffectiveTenants();
        if (effective.isEmpty()) {
            log.warn("TUGDK GPS API enabled but no usable tenants configured (no tenants[] and no fallback token). Provider will be inactive.");
            return Map.of();
        }

        Map<String, TugdkTenantClient> clients = new LinkedHashMap<>();
        for (TugdkTenantsProperties.TenantConfig tenant : effective) {
            WebClient webClient = buildTenantWebClient(tenantsProperties, tenant.getId());
            TugdkTenantClient client = new TugdkTenantClient(
                    tenant.getId(),
                    webClient,
                    tenant.getToken(),
                    mapper,
                    commonProperties,
                    tenantsProperties.getCacheTimeout(),
                    healthMonitor
            );
            clients.put(tenant.getId(), client);
            log.info("Registered TUGDK tenant client: id={} baseUrl={} cacheTimeout={}s",
                    tenant.getId(), tenantsProperties.getBaseUrl(), tenantsProperties.getCacheTimeout().toSeconds());
        }
        return Map.copyOf(clients);
    }

    private WebClient buildTenantWebClient(TugdkTenantsProperties props, String tenantId) {
        if (props.getBaseUrl() == null || props.getBaseUrl().isBlank()) {
            throw new IllegalStateException(
                    "external.api.gps-tugdk.base-url must be configured when gps-tugdk is enabled");
        }

        HttpClient httpClient = HttpClient.create()
                .responseTimeout(props.getTimeout())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) props.getConnectTimeout().toMillis())
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(props.getReadTimeout().toSeconds(), TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(props.getWriteTimeout().toSeconds(), TimeUnit.SECONDS)));

        log.debug("TUGDK tenant {} WebClient configured: baseUrl={} response={}s connect={}s read={}s write={}s",
                tenantId,
                props.getBaseUrl(),
                props.getTimeout().toSeconds(),
                props.getConnectTimeout().toSeconds(),
                props.getReadTimeout().toSeconds(),
                props.getWriteTimeout().toSeconds());

        return WebClient.builder()
                .baseUrl(props.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
                .build();
    }

}
