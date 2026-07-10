package biz.ugur.busroutebackend.prediction.shadow;

import biz.ugur.busroutebackend.prediction.core.GeometryFixture;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.RouteGeometryCache;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class V31ShadowIsolationTest {

    private static List<double[]> straightLine() {
        List<double[]> pts = new ArrayList<>();
        for (int i = 0; i <= 60; i++) {
            pts.add(new double[]{38.0, 58.0 + i * 0.001});
        }
        return pts;
    }

    private static V31Fix fix(int i) {
        return new V31Fix("veh-shadow-0001", "TEST 01", "99t", 38.0, 58.0 + i * 0.0008,
                30.0, 90.0, true, Instant.parse("2026-07-09T05:00:00Z").plusSeconds(i * 5L), 0);
    }

    private static RouteGeometryCache cacheStub() {
        RouteGeometryCache cache = Mockito.mock(RouteGeometryCache.class);
        Mockito.when(cache.getPoints(Mockito.eq("99t"), Mockito.anyInt()))
                .thenReturn(straightLine());
        Mockito.when(cache.getRouteStops(Mockito.eq("99t"), Mockito.anyInt()))
                .thenReturn(List.of());
        return cache;
    }

    @Test
    void b2TapProcessesTicksAndMainStreamIsUntouched(@org.junit.jupiter.api.io.TempDir Path dir)
            throws Exception {
        V31ShadowTap tap = new V31ShadowTap();
        V31ShadowService service = new V31ShadowService(tap, new V31RouteLines(cacheStub()),
                Clock.systemUTC(), dir);
        List<V31Fix> fixes = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            fixes.add(fix(i));
        }
        Flux<V31Fix> mainStream = Flux.fromIterable(fixes).doOnNext(tap::accept);
        List<V31Fix> withTap = mainStream.collectList().block();
        assertThat(withTap).containsExactlyElementsOf(fixes);
        List<V31Fix> withoutTap = Flux.fromIterable(fixes).collectList().block();
        assertThat(withTap).as("выхлоп основного потока идентичен off-прогону, 0 расхождений")
                .containsExactlyElementsOf(withoutTap);
        await().atMost(java.time.Duration.ofSeconds(10))
                .until(() -> service.v31TicksProcessed() > 0);
        assertThat(service.v31TicksProcessed()).isPositive();
        assertThat(service.v31ErrorCount()).isZero();
        service.shutdown();
    }

    @Test
    void b3ExceptionInsideV31DoesNotBreakMainStream(@org.junit.jupiter.api.io.TempDir Path dir) {
        V31ShadowTap tap = new V31ShadowTap();
        V31RouteLines lines = Mockito.mock(V31RouteLines.class);
        Mockito.when(lines.topologyFor(Mockito.anyString()))
                .thenThrow(new IllegalStateException("injected v31 failure"))
                .thenReturn(null);
        V31ShadowService service = new V31ShadowService(tap, lines, Clock.systemUTC(), dir);
        List<V31Fix> fixes = List.of(fix(0), fix(1), fix(2));
        StepVerifier.create(Flux.fromIterable(fixes).doOnNext(tap::accept))
                .expectNextCount(3)
                .verifyComplete();
        await().atMost(java.time.Duration.ofSeconds(10))
                .until(() -> service.v31ErrorCount() >= 1);
        assertThat(service.v31ErrorCount()).isEqualTo(1);
        service.shutdown();
    }
}
