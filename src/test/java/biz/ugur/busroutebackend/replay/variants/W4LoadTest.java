package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.broadcast.V31BroadcastLoop;
import biz.ugur.busroutebackend.prediction.broadcast.V31BroadcastProperties;
import biz.ugur.busroutebackend.prediction.broadcast.V31FrameSink;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import biz.ugur.busroutebackend.prediction.shadow.V31Fix;
import biz.ugur.busroutebackend.prediction.shadow.V31RouteLines;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowTap;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class W4LoadTest {

    @Test
    @EnabledIfSystemProperty(named = "w4.load", matches = "true")
    void loadTickP99() throws Exception {
        int vehicles = Integer.getInteger("w4.load.vehicles", 1500);
        int subscribers = Integer.getInteger("w4.load.subs", 5000);
        int ticks = Integer.getInteger("w4.load.ticks", 120);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-13T10:00:00Z"));
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        RouteTopology topo = RouteTopology.thereAndBack(
                Variant61FixturesTest.FULL_0, Variant61FixturesTest.FULL_1);
        V31RouteLines lines = new V31RouteLines(null) {
            @Override
            public RouteTopology topologyFor(String r) {
                return topo;
            }
        };
        V31ShadowTap tap = Mockito.mock(V31ShadowTap.class);
        Mockito.when(tap.flux()).thenReturn(reactor.core.publisher.Flux.never());
        Path dir = Files.createTempDirectory("w4-load");
        V31ShadowService shadow = new V31ShadowService(tap, lines, clock, dir, 1L);
        V31BroadcastProperties props = new V31BroadcastProperties();
        props.setBroadcast(V31BroadcastProperties.Mode.LIVE);
        V31FrameSink sink = new V31FrameSink();
        AtomicLong delivered = new AtomicLong();
        for (int i = 0; i < subscribers; i++) {
            sink.asFlux().subscribe(b -> delivered.addAndGet(b.size()));
        }
        V31BroadcastLoop loop = new V31BroadcastLoop(shadow, props, sink,
                new ObjectMapper(), clock, dir);
        double L = Variant61FixturesTest.FULL_0.totalMeters();
        for (int i = 0; i < vehicles; i++) {
            double s = (i * 37.0) % L;
            double[] p = Variant61FixturesTest.FULL_0.pointAtS(s);
            shadow.processForReplay(new V31Fix("veh-" + String.format("%06d", i), "TM " + i,
                    "61", p[0], p[1], 30.0, 0.0, true, now.get(), 0, 0.8, 12, 0.0));
        }
        long[] tickNanos = new long[ticks];
        for (int k = 0; k < ticks; k++) {
            now.set(now.get().plusSeconds(1));
            long t0 = System.nanoTime();
            loop.tick();
            tickNanos[k] = System.nanoTime() - t0;
        }
        long[] sorted = tickNanos.clone();
        Arrays.sort(sorted);
        long p50 = sorted[ticks / 2] / 1_000_000;
        long p99 = sorted[(int) (ticks * 0.99) - 1] / 1_000_000;
        long max = sorted[ticks - 1] / 1_000_000;
        long rssKb = -1;
        try {
            for (String ln : Files.readAllLines(Path.of("/proc/self/status"))) {
                if (ln.startsWith("VmRSS:")) {
                    rssKb = Long.parseLong(ln.replaceAll("\\D+", ""));
                }
            }
        } catch (Exception ignored) { }
        System.out.printf("w4-load: борты=%d подписки=%d тиков=%d кадров=%d доставлено=%d "
                        + "tick p50=%dms p99=%dms max=%dms RSS=%dMB%n",
                vehicles, subscribers, ticks, loop.framesEmitted(), delivered.get(),
                p50, p99, max, rssKb / 1024);
        assertThat(p99).isLessThanOrEqualTo(500L);
    }
}
