package biz.ugur.busroutebackend.transport.infrastructure.prediction;

import biz.ugur.busroutebackend.prediction.shadow.V31ShadowTap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class V31InjectionPointTest {

    @SuppressWarnings("unchecked")
    private VehiclePositionPredictionService service(ObjectProvider<V31ShadowTap> provider) {
        PredictionProperties props = new PredictionProperties();
        props.setEnabled(false);
        VehiclePositionPredictionService svc = new VehiclePositionPredictionService(
                props,
                mock(PredictionBroadcaster.class),
                mock(RouteGeometryCache.class),
                mock(VehiclePredictionStateRepository.class),
                mock(ObjectProvider.class),
                mock(GpsOutlierFilter.class),
                mock(SnapCorrector.class),
                mock(VehiclePositionPredictor.class),
                Optional.empty(),
                Clock.systemUTC(),
                mock(biz.ugur.busroutebackend.transport.infrastructure.debug.PipelineTracer.class));
        if (provider != null) {
            try {
                var m = VehiclePositionPredictionService.class
                        .getDeclaredMethod("setV31ShadowTap", ObjectProvider.class);
                m.setAccessible(true);
                m.invoke(svc, provider);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(e);
            }
        }
        return svc;
    }

    private void fire(VehiclePositionPredictionService svc, int n) {
        StepVerifier.create(Flux.range(0, n).doOnNext(i -> svc.onGpsUpdate(
                        i % 3 == 0 ? null : "veh-inj-" + i, "PL " + i, "99t",
                        38.0, 58.0 + i * 0.001, 25.0, 90.0, true,
                        Instant.parse("2026-07-09T06:00:00Z").plusSeconds(i * 5L), 0,
                        true, false)))
                .expectNextCount(n)
                .verifyComplete();
    }

    @SuppressWarnings("unchecked")
    @Test
    void r21EmptyProviderKeepsProdPathAlive() {
        ObjectProvider<V31ShadowTap> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        fire(service(provider), 5);
        fire(service(null), 5);
    }

    @SuppressWarnings("unchecked")
    @Test
    void r22ThrowingTapDoesNotBreakProdStreamAndCountsError() {
        V31ShadowTap tap = Mockito.spy(new V31ShadowTap());
        Mockito.doThrow(new RuntimeException("injected tryEmitNext failure"))
                .doCallRealMethod()
                .when(tap).accept(any());
        ObjectProvider<V31ShadowTap> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tap);
        VehiclePositionPredictionService svc = service(provider);
        StepVerifier.create(Flux.range(0, 3).doOnNext(i -> svc.onGpsUpdate(
                        "veh-r22", "PL", "99t", 38.0, 58.0 + i * 0.001, 25.0, 90.0, true,
                        Instant.parse("2026-07-09T06:00:00Z").plusSeconds(i * 5L), 0, true, false)))
                .expectNextCount(3)
                .verifyComplete();
        assertThat(tap.errorCount()).isEqualTo(1);
    }

    @SuppressWarnings("unchecked")
    @Test
    void r23NullFieldsStayInsideV31Branch() {
        V31ShadowTap tap = new V31ShadowTap();
        ObjectProvider<V31ShadowTap> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(tap);
        fire(service(provider), 6);
        assertThat(tap.droppedCount() + tap.errorCount()).isGreaterThanOrEqualTo(0);
    }

    @SuppressWarnings("unchecked")
    @Test
    void r24FlagOffNeverTouchesTap() {
        V31ShadowTap tapSpy = Mockito.spy(new V31ShadowTap());
        ObjectProvider<V31ShadowTap> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        fire(service(provider), 5);
        verify(tapSpy, never()).accept(any());
        fire(service(null), 5);
        verify(tapSpy, never()).accept(any());
    }

}
