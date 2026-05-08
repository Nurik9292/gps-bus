package biz.ugur.busroutebackend.shared.infrastructure.external.gps;

import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsFetchOptimizationProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.GpsProviderProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.config.TugdkTenantsProperties;
import biz.ugur.busroutebackend.shared.infrastructure.external.gps.metrics.GpsFetchStrategyMetricsRecorder;
import biz.ugur.busroutebackend.transport.application.dto.GpsPositionDTO;
import biz.ugur.busroutebackend.transport.domain.valueobject.GpsProviderType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TugdkGpsDataProviderTest {

    @Mock
    private TugdkTenantClient ashgabatClient;
    @Mock
    private TugdkTenantClient balkanClient;
    @Mock
    private VehicleTenantResolver tenantResolver;
    @Mock
    private GpsFetchStrategyMetricsRecorder metricsRecorder;

    private TugdkTenantsProperties tenantsProperties;
    private GpsProviderProperties commonProperties;
    private GpsFetchOptimizationProperties optimizationProperties;
    private TugdkGpsDataProvider provider;

    @BeforeEach
    void setUp() {
        tenantsProperties = new TugdkTenantsProperties();
        tenantsProperties.setEnabled(true);

        commonProperties = new GpsProviderProperties();
        optimizationProperties = new GpsFetchOptimizationProperties();
        optimizationProperties.setSelectiveFetchEnabled(false);

        lenient().when(ashgabatClient.tenantId()).thenReturn("ASHGABAT");
        lenient().when(balkanClient.tenantId()).thenReturn("BALKAN");

        provider = new TugdkGpsDataProvider(
                tenantsProperties,
                commonProperties,
                optimizationProperties,
                metricsRecorder,
                tenantResolver,
                Map.of("ASHGABAT", ashgabatClient, "BALKAN", balkanClient)
        );
    }

    private GpsPositionDTO position(String deviceId) {
        GpsPositionDTO p = new GpsPositionDTO();
        p.setDeviceId(deviceId);
        p.setLatitude(37.0);
        p.setLongitude(58.0);
        return p;
    }

    @Test
    void getProviderTypeReturnsTugdk() {
        assertThat(provider.getProviderType()).isEqualTo(GpsProviderType.TUGDK);
    }

    @Test
    void isEnabledReportsFalseWhenNoTenants() {
        TugdkGpsDataProvider empty = new TugdkGpsDataProvider(
                tenantsProperties, commonProperties, optimizationProperties,
                metricsRecorder, tenantResolver, Map.of());

        assertThat(empty.isEnabled()).isFalse();
    }

    @Test
    void fetchPositionsByDeviceIdsRoutesToCorrectTenantsInParallel() {
        when(tenantResolver.groupByTenant(List.of("dev-a1", "dev-b1", "dev-a2")))
                .thenReturn(Map.of(
                        "ASHGABAT", List.of("dev-a1", "dev-a2"),
                        "BALKAN",   List.of("dev-b1")));

        when(ashgabatClient.fetchAllPositions())
                .thenReturn(Mono.just(List.of(position("dev-a1"), position("dev-a2"), position("dev-other"))));
        when(balkanClient.fetchAllPositions())
                .thenReturn(Mono.just(List.of(position("dev-b1"), position("dev-stranger"))));

        StepVerifier.create(provider.fetchPositionsByDeviceIds(List.of("dev-a1", "dev-b1", "dev-a2")))
                .assertNext(list -> assertThat(list)
                        .extracting(GpsPositionDTO::getDeviceId)
                        .containsExactlyInAnyOrder("dev-a1", "dev-a2", "dev-b1"))
                .verifyComplete();

        verify(ashgabatClient, times(1)).fetchAllPositions();
        verify(balkanClient, times(1)).fetchAllPositions();
    }

    @Test
    void fetchPositionsByDeviceIdsSkipsTenantsWithoutClient() {
        when(tenantResolver.groupByTenant(List.of("dev-x")))
                .thenReturn(Map.of("UNKNOWN_TENANT", List.of("dev-x")));

        StepVerifier.create(provider.fetchPositionsByDeviceIds(List.of("dev-x")))
                .assertNext(list -> assertThat(list).isEmpty())
                .verifyComplete();

        verify(ashgabatClient, never()).fetchAllPositions();
        verify(balkanClient, never()).fetchAllPositions();
    }

    @Test
    void fetchPositionsByDeviceIdsReturnsEmptyForEmptyInput() {
        StepVerifier.create(provider.fetchPositionsByDeviceIds(List.of()))
                .assertNext(list -> assertThat(list).isEmpty())
                .verifyComplete();

        verify(tenantResolver, never()).groupByTenant(anyString() == null ? null : List.of());
    }

    @Test
    void fetchAllPositionsAggregatesAcrossAllTenants() {
        when(ashgabatClient.fetchAllPositions())
                .thenReturn(Mono.just(List.of(position("dev-a1"))));
        when(balkanClient.fetchAllPositions())
                .thenReturn(Mono.just(List.of(position("dev-b1"))));

        StepVerifier.create(provider.fetchAllPositions())
                .assertNext(list -> assertThat(list)
                        .extracting(GpsPositionDTO::getDeviceId)
                        .containsExactlyInAnyOrder("dev-a1", "dev-b1"))
                .verifyComplete();
    }

    @Test
    void healthCheckIsTrueOnlyWhenAllTenantsHealthy() {
        lenient().when(ashgabatClient.healthCheck()).thenReturn(Mono.just(true));
        lenient().when(balkanClient.healthCheck()).thenReturn(Mono.just(false));

        StepVerifier.create(provider.healthCheck())
                .assertNext(healthy -> assertThat(healthy).isFalse())
                .verifyComplete();
    }

    @Test
    void selectiveFetchIsUsedWhenEnabledAndUnderThreshold() {
        optimizationProperties.setSelectiveFetchEnabled(true);
        optimizationProperties.setSelectiveFetchThreshold(5);
        optimizationProperties.setParallelFetchConcurrency(2);

        when(tenantResolver.groupByTenant(List.of("dev-a1", "dev-a2")))
                .thenReturn(Map.of("ASHGABAT", List.of("dev-a1", "dev-a2")));
        when(ashgabatClient.fetchSingleDevicePosition("dev-a1"))
                .thenReturn(Mono.just(position("dev-a1")));
        when(ashgabatClient.fetchSingleDevicePosition("dev-a2"))
                .thenReturn(Mono.just(position("dev-a2")));

        StepVerifier.create(provider.fetchPositionsByDeviceIds(List.of("dev-a1", "dev-a2")))
                .assertNext(list -> assertThat(list)
                        .extracting(GpsPositionDTO::getDeviceId)
                        .containsExactlyInAnyOrder("dev-a1", "dev-a2"))
                .verifyComplete();

        verify(ashgabatClient, never()).fetchAllPositions();
        verify(metricsRecorder, times(1)).recordSelectiveFetch(anyInt(), anyString());
    }
}
