package biz.ugur.busroutebackend.prediction.broadcast;

import biz.ugur.busroutebackend.prediction.core.HypothesisBank;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.shadow.V31Fix;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V31BroadcastLoopTest {

    @Mock
    private V31ShadowService shadow;
    @Mock
    private MotionFilterCore core;
    @Mock
    private HypothesisBank bank;
    @Mock
    private HypothesisBank.Hypothesis leader;
    @Mock
    private RouteLine geom;

    private final AtomicReference<Instant> now =
            new AtomicReference<>(Instant.parse("2026-07-13T10:00:00Z"));
    private V31BroadcastProperties props;
    private V31FrameSink sink;
    private ObjectMapper mapper;
    private V31BroadcastLoop loop;
    private List<List<V31FrameEnvelope>> received;

    private static final V31Fix RAW = new V31Fix("veh-0001-abcd", "TM 01", "61",
            38.05, 58.17, 30.0, 123.0, true, Instant.parse("2026-07-13T09:59:59Z"),
            0, 0.8, 12, 0.0);

    @BeforeEach
    void setUp() {
        props = new V31BroadcastProperties();
        props.setBroadcast(V31BroadcastProperties.Mode.LIVE);
        sink = new V31FrameSink();
        mapper = spy(new ObjectMapper());
        Clock clock = new Clock() {
            public ZoneOffset getZone() { return ZoneOffset.UTC; }
            public Clock withZone(java.time.ZoneId zone) { return this; }
            public Instant instant() { return now.get(); }
        };
        loop = new V31BroadcastLoop(shadow, props, sink, mapper, clock,
                Path.of("target/w4-test-frames"));
        received = new java.util.concurrent.CopyOnWriteArrayList<>();
        sink.asFlux().subscribe(received::add);
        when(shadow.coresView()).thenReturn(Map.of("veh-0001-abcd", core));
        when(shadow.lastFixOf("veh-0001-abcd")).thenReturn(RAW);
        when(core.bank()).thenReturn(bank);
        when(bank.leader()).thenReturn(leader);
        when(leader.geom()).thenReturn(geom);
        when(geom.pointAtS(1000.0)).thenReturn(new double[]{38.06, 58.18});
        when(geom.courseAt(1000.0)).thenReturn(45.0);
        when(geom.totalMeters()).thenReturn(31463.9);
        when(core.lastFixAt()).thenReturn(RAW.timestamp());
        when(core.tripId()).thenReturn(7L);
        when(core.positionVariance()).thenReturn(100.0);
        when(core.direction()).thenReturn(0);
    }

    private void modeIs(String mode) {
        when(core.broadcastTick(org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(new PredictionModel.Estimate(1000.0, 5.0, mode, 100.0));
    }

    private JsonNode lastFrame() throws Exception {
        List<V31FrameEnvelope> batch = received.get(received.size() - 1);
        return mapper.readTree(batch.get(batch.size() - 1).json());
    }

    @Test
    void suppressedModesEmitNothing() {
        for (String m : List.of("NO_GPS", "ACQUIRING")) {
            modeIs(m);
            loop.tick();
        }
        assertThat(received).isEmpty();
    }

    @Test
    void garageVehicleEmitsNothingEvenWhenTracking() {
        V31Fix parked = new V31Fix(RAW.vehicleId(), RAW.licensePlate(), RAW.routeNumber(),
                RAW.routeId(), RAW.latitude(), RAW.longitude(), 0.0, RAW.course(),
                false, RAW.timestamp(), RAW.direction(),
                RAW.hdop(), RAW.satellites(), RAW.accuracy(), true);
        when(shadow.lastFixOf("veh-0001-abcd")).thenReturn(parked);
        modeIs("TRACKING");
        loop.tick();
        assertThat(received).isEmpty();
        assertThat(loop.garageSuppressed()).isEqualTo(1);
    }

    @Test
    void trackingEmitsPredOnLineWithEta() throws Exception {
        modeIs("TRACKING");
        loop.tick();
        JsonNode f = lastFrame();
        assertThat(f.get("src").asText()).isEqualTo("PRED");
        assertThat(f.get("latitude").asDouble()).isEqualTo(38.06);
        assertThat(f.get("dir").asDouble()).isEqualTo(45.0);
        assertThat(f.get("eta_reliable").asBoolean()).isTrue();
        assertThat(f.get("trip_seq").asLong()).isEqualTo(7L);
        assertThat(f.get("t_gps").asText()).isEqualTo("2026-07-13T09:59:59Z");
        assertThat(f.get("mode").asText()).isEqualTo("TRACKING");
        assertThat(f.get("fraction").asDouble()).isBetween(0.03, 0.04);
    }

    @Test
    void unreliableEtaModes() throws Exception {
        for (String m : List.of("TURNING", "GPS_LOST", "RECOVERING")) {
            received.clear();
            modeIs(m);
            now.set(now.get().plusSeconds(10));
            loop.tick();
            JsonNode f = lastFrame();
            assertThat(f.get("eta_reliable").asBoolean()).as(m).isFalse();
            assertThat(f.get("src").asText()).as(m).isEqualTo("PRED");
        }
    }

    @Test
    void offRouteEmitsRawWithFlag() throws Exception {
        modeIs("OFF_ROUTE");
        loop.tick();
        JsonNode f = lastFrame();
        assertThat(f.get("src").asText()).isEqualTo("RAW");
        assertThat(f.get("latitude").asDouble()).isEqualTo(RAW.latitude());
        assertThat(f.get("dir").asDouble()).isEqualTo(123.0);
        assertThat(f.get("off_route").asBoolean()).isTrue();
        assertThat(f.get("eta_reliable").asBoolean()).isFalse();
        assertThat(f.has("fraction")).isFalse();
    }

    @Test
    void unchangedFrameSuppressedUntilHeartbeat() {
        modeIs("TRACKING");
        loop.tick();
        now.set(now.get().plusSeconds(1));
        loop.tick();
        assertThat(received).hasSize(1);
        assertThat(loop.framesSuppressed()).isEqualTo(1);
        now.set(now.get().plusSeconds(5));
        loop.tick();
        assertThat(received).hasSize(2);
    }

    @Test
    void serializationExactlyOncePerEmittedFrame() throws Exception {
        modeIs("TRACKING");
        loop.tick();
        now.set(now.get().plusSeconds(1));
        loop.tick();
        verify(mapper, times(1)).writeValueAsString(org.mockito.ArgumentMatchers.any(V31Frame.class));
        assertThat(loop.serializations()).isEqualTo(1);
    }

    @Test
    void confDropsAsVarianceGrows() throws Exception {
        modeIs("TRACKING");
        loop.tick();
        double conf1 = lastFrame().get("conf").asDouble();
        when(core.positionVariance()).thenReturn(5000.0);
        now.set(now.get().plusSeconds(6));
        loop.tick();
        double conf2 = lastFrame().get("conf").asDouble();
        assertThat(conf2).isLessThan(conf1);
    }

    @Test
    void tripSeqChangeEmitsAndChecksBoundaryJump() throws Exception {
        modeIs("TRACKING");
        loop.tick();
        when(core.tripId()).thenReturn(8L);
        when(geom.pointAtS(1000.0)).thenReturn(new double[]{38.10, 58.30});
        now.set(now.get().plusSeconds(1));
        loop.tick();
        JsonNode f = lastFrame();
        assertThat(f.get("trip_seq").asLong()).isEqualTo(8L);
        assertThat(loop.boundaryCapPrints()).isEqualTo(1);
    }

    @Test
    void allowlistFiltersRoutes() {
        props.setRoutesAllowlist(java.util.Set.of("25"));
        modeIs("TRACKING");
        loop.tick();
        assertThat(received).isEmpty();
    }
}
