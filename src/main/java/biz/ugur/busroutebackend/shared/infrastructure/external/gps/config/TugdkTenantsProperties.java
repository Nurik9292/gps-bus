package biz.ugur.busroutebackend.shared.infrastructure.external.gps.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "external.api.gps-tugdk")
public class TugdkTenantsProperties {

    public static final String DEFAULT_TENANT_ID = "ASHGABAT";

    private boolean enabled = false;
    private String baseUrl;
    private Duration timeout = Duration.ofSeconds(30);
    private Duration connectTimeout = Duration.ofSeconds(10);
    private Duration readTimeout = Duration.ofSeconds(60);
    private Duration writeTimeout = Duration.ofSeconds(10);
    private Duration cacheTimeout = Duration.ofSeconds(3);

    private String token;

    private List<TenantConfig> tenants = new ArrayList<>();

    public List<TenantConfig> resolveEffectiveTenants() {
        if (tenants != null && !tenants.isEmpty()) {
            return tenants.stream()
                    .filter(t -> t.getId() != null && !t.getId().isBlank())
                    .filter(t -> t.getToken() != null && !t.getToken().isBlank())
                    .toList();
        }
        if (token != null && !token.isBlank()) {
            TenantConfig fallback = new TenantConfig();
            fallback.setId(DEFAULT_TENANT_ID);
            fallback.setToken(token);
            return List.of(fallback);
        }
        return List.of();
    }

    @Getter
    @Setter
    public static class TenantConfig {
        private String id;
        private String token;
    }
}
