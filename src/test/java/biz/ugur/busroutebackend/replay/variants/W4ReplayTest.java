package biz.ugur.busroutebackend.replay.variants;

import biz.ugur.busroutebackend.prediction.broadcast.V31BroadcastLoop;
import biz.ugur.busroutebackend.prediction.shadow.V31Fix;
import biz.ugur.busroutebackend.prediction.shadow.V31RouteLines;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowTap;
import biz.ugur.busroutebackend.prediction.broadcast.V31BroadcastProperties;
import biz.ugur.busroutebackend.prediction.broadcast.V31FrameSink;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.Mockito;

import java.io.BufferedReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class W4ReplayTest {

    @Test
    @EnabledIfSystemProperty(named = "w4.corpus", matches = ".+")
    void replayCorpusWithOneHzTicks() throws Exception {
        String corpus = System.getProperty("w4.corpus");
        Path framesDir = Path.of(System.getProperty("w4.frames", "target/w4-frames"));
        Files.createDirectories(framesDir);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.EPOCH);
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
                var topoBase = biz.ugur.busroutebackend.prediction.core.RouteTopology
                .thereAndBack(Variant61FixturesTest.FULL_0,
                        Variant61FixturesTest.FULL_1)
                .withVariants(List.of(
                        Variant61FixturesTest.gokje0().shortVariant(),
                        Variant61FixturesTest.gokjeTail1().shortVariant()));
        String cz = System.getProperty("w4.cityzone", "");
        final biz.ugur.busroutebackend.prediction.core.RouteTopology topo;
        if (!cz.isBlank()) {
            String[] p = cz.split(",");
            topo = topoBase.withCityZone(
                    new biz.ugur.busroutebackend.prediction.core.RouteTopology.CityZone(
                            Double.parseDouble(p[0]), Double.parseDouble(p[1]),
                            Double.parseDouble(p[2]), Double.parseDouble(p[3])));
        } else {
            topo = topoBase;
        }
        V31RouteLines lines = new V31RouteLines(null) {
            @Override
            public biz.ugur.busroutebackend.prediction.core.RouteTopology topologyFor(String r) {
                return "61".equals(r) ? topo : null;
            }
        };
        V31ShadowTap tap = Mockito.mock(V31ShadowTap.class);
        Mockito.when(tap.flux()).thenReturn(reactor.core.publisher.Flux.never());
        V31ShadowService shadow = new V31ShadowService(tap, lines, clock,
                framesDir.resolve("shadowlogs"), 1_000_000L);
        V31BroadcastProperties props = new V31BroadcastProperties();
        props.setBroadcast(V31BroadcastProperties.Mode.SHADOW);
        ObjectMapper mapper = new ObjectMapper();
        V31BroadcastLoop loop = new V31BroadcastLoop(shadow, props, new V31FrameSink(),
                mapper, clock, framesDir);
        long fixes = 0;
        long ticks = 0;
        long dupTs = 0;
        java.util.Map<String, Instant> prevTs = new java.util.HashMap<>();
        List<Path> files = Files.list(Path.of(corpus))
                .filter(f -> f.getFileName().toString().startsWith("campaign-"))
                .sorted().toList();
        for (Path f : files) {
            try (BufferedReader r = Files.newBufferedReader(f)) {
                String ln;
                while ((ln = r.readLine()) != null) {
                    JsonNode j;
                    try {
                        j = mapper.readTree(ln);
                    } catch (Exception e) {
                        continue;
                    }
                    if (!"61".equals(j.path("routeNumber").asText())) continue;
                    Instant ts = Instant.parse(j.get("timestamp").asText());
                    if (now.get().equals(Instant.EPOCH)) now.set(ts);
                    while (now.get().plusSeconds(1).isBefore(ts)) {
                        now.set(now.get().plusSeconds(1));
                        loop.tick();
                        ticks++;
                    }
                    if (ts.isAfter(now.get())) {
                        now.set(ts);
                    }
                    String vidKey = j.get("vehicleId").asText();
                    if (ts.equals(prevTs.put(vidKey, ts))) {
                        dupTs++;
                    }
                    shadow.processForReplay(new V31Fix(j.get("vehicleId").asText(),
                            j.path("licensePlate").asText(""), "61",
                            j.get("latitude").asDouble(), j.get("longitude").asDouble(),
                            j.path("speedKmh").asDouble(0), j.path("course").asDouble(0),
                            j.path("inMotion").asBoolean(false), ts,
                            j.path("direction").asInt(0), null, null, null));
                    loop.tick();
                    ticks++;
                    fixes++;
                }
            }
        }
        System.out.printf("w4-replay: файлов=%d фиксов61=%d тиков=%d кадров=%d подавлено=%d "
                        + "сериализаций=%d №14-принтов=%d%n",
                files.size(), fixes, ticks, loop.framesEmitted(), loop.framesSuppressed(),
                loop.serializations(), loop.boundaryCapPrints());
        System.out.printf("w4-replay: записано=%d ошибок-записи=%d дубль-ts-фиксов=%d%n",
                loop.framesWritten(), loop.shadowWriteErrors(), dupTs);
        assertThat(loop.framesEmitted()).isGreaterThan(0);
        assertThat(loop.serializations()).isEqualTo(loop.framesEmitted());
    }
}
