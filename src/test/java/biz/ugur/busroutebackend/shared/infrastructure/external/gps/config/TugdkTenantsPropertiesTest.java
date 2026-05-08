package biz.ugur.busroutebackend.shared.infrastructure.external.gps.config;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TugdkTenantsPropertiesTest {

    @Test
    void resolveEffectiveTenantsReturnsExplicitListWhenProvided() {
        TugdkTenantsProperties props = new TugdkTenantsProperties();
        props.setTenants(List.of(
                tenant("ASHGABAT", "tok-a"),
                tenant("BALKAN", "tok-b")
        ));

        List<TugdkTenantsProperties.TenantConfig> resolved = props.resolveEffectiveTenants();

        assertThat(resolved).extracting(TugdkTenantsProperties.TenantConfig::getId)
                .containsExactly("ASHGABAT", "BALKAN");
    }

    @Test
    void resolveEffectiveTenantsDropsEntriesWithBlankIdOrToken() {
        TugdkTenantsProperties props = new TugdkTenantsProperties();
        List<TugdkTenantsProperties.TenantConfig> mixed = new ArrayList<>();
        mixed.add(tenant("ASHGABAT", "tok-a"));
        mixed.add(tenant("BALKAN", ""));
        mixed.add(tenant(" ", "tok-x"));
        mixed.add(tenant(null, "tok-y"));
        props.setTenants(mixed);

        List<TugdkTenantsProperties.TenantConfig> resolved = props.resolveEffectiveTenants();

        assertThat(resolved).extracting(TugdkTenantsProperties.TenantConfig::getId)
                .containsExactly("ASHGABAT");
    }

    @Test
    void resolveEffectiveTenantsFallsBackToLegacyTokenAsAshgabat() {
        TugdkTenantsProperties props = new TugdkTenantsProperties();
        props.setToken("legacy-tok");

        List<TugdkTenantsProperties.TenantConfig> resolved = props.resolveEffectiveTenants();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).getId()).isEqualTo(TugdkTenantsProperties.DEFAULT_TENANT_ID);
        assertThat(resolved.get(0).getToken()).isEqualTo("legacy-tok");
    }

    @Test
    void resolveEffectiveTenantsReturnsEmptyWhenNothingConfigured() {
        TugdkTenantsProperties props = new TugdkTenantsProperties();
        assertThat(props.resolveEffectiveTenants()).isEmpty();
    }

    @Test
    void explicitTenantsTakePrecedenceOverLegacyToken() {
        TugdkTenantsProperties props = new TugdkTenantsProperties();
        props.setToken("legacy-tok");
        props.setTenants(List.of(tenant("ASHGABAT", "explicit-tok")));

        List<TugdkTenantsProperties.TenantConfig> resolved = props.resolveEffectiveTenants();

        assertThat(resolved).hasSize(1);
        assertThat(resolved.get(0).getToken())
                .as("explicit tenants[] override legacy 'token' field")
                .isEqualTo("explicit-tok");
    }

    private TugdkTenantsProperties.TenantConfig tenant(String id, String token) {
        TugdkTenantsProperties.TenantConfig t = new TugdkTenantsProperties.TenantConfig();
        t.setId(id);
        t.setToken(token);
        return t;
    }
}
