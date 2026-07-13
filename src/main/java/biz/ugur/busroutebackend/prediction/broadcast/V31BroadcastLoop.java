package biz.ugur.busroutebackend.prediction.broadcast;

import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.prediction.core.RouteLine;
import biz.ugur.busroutebackend.prediction.shadow.V31Fix;
import biz.ugur.busroutebackend.prediction.shadow.V31ShadowService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class V31BroadcastLoop {

    private static final Logger log = LoggerFactory.getLogger(V31BroadcastLoop.class);
    private static final Set<String> SUPPRESSED_MODES = Set.of("NO_GPS", "ACQUIRING");
    private static final Set<String> UNRELIABLE_ETA_MODES =
            Set.of("TURNING", "GPS_LOST", "RECOVERING", "OFF_ROUTE");
    private static final DateTimeFormatter HOUR =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH").withZone(ZoneOffset.UTC);
    private static final double BOUNDARY_SANITY_CAP_METERS = 150.0;

    private final V31ShadowService shadow;
    private final V31BroadcastProperties props;
    private final V31FrameSink frameSink;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final Path logDirectory;

    private final Map<String, SentState> lastSent = new ConcurrentHashMap<>();
    private final AtomicLong framesEmitted = new AtomicLong();
    private final AtomicLong framesSuppressed = new AtomicLong();
    private final AtomicLong serializations = new AtomicLong();
    private final AtomicLong boundaryCapPrints = new AtomicLong();
    private final AtomicLong framesWritten = new AtomicLong();
    private final AtomicLong shadowWriteErrors = new AtomicLong();
    private String writerHour;
    private BufferedWriter frameWriter;

    private record SentState(long qS, String mode, String src, long tripSeq, long qConf,
                             Instant sentAt, double lat, double lon, long tripSeqSent) {
    }

    public V31BroadcastLoop(V31ShadowService shadow, V31BroadcastProperties props,
                            V31FrameSink frameSink, ObjectMapper mapper, Clock clock,
                            Path logDirectory) {
        this.shadow = shadow;
        this.props = props;
        this.frameSink = frameSink;
        this.mapper = mapper;
        this.clock = clock;
        this.logDirectory = logDirectory;
    }

    public void tick() {
        if (props.getBroadcast() == V31BroadcastProperties.Mode.OFF) {
            return;
        }
        Instant now = clock.instant();
        List<V31FrameEnvelope> batch = new ArrayList<>();
        for (Map.Entry<String, MotionFilterCore> e : shadow.coresView().entrySet()) {
            String vehicleId = e.getKey();
            MotionFilterCore core = e.getValue();
            V31Fix raw = shadow.lastFixOf(vehicleId);
            if (raw == null || !props.routeInScope(raw.routeNumber())) {
                continue;
            }
            V31Frame frame;
            synchronized (core) {
                PredictionModel.Estimate est = core.broadcastTick(now);
                if (est == null || SUPPRESSED_MODES.contains(est.mode())) {
                    continue;
                }
                frame = buildFrame(core, est, raw, now);
            }
            if (frame == null || suppressUnchanged(vehicleId, frame, now)) {
                continue;
            }
            try {
                String json = mapper.writeValueAsString(frame);
                serializations.incrementAndGet();
                batch.add(new V31FrameEnvelope(frame.routeNumber(), vehicleId, json));
                framesEmitted.incrementAndGet();
                if (props.getBroadcast() == V31BroadcastProperties.Mode.SHADOW) {
                    writeShadow(json, now);
                }
            } catch (IOException ex) {
                log.debug("v31 frame serialize failed: {}", ex.getMessage());
            }
        }
        if (props.getBroadcast() == V31BroadcastProperties.Mode.LIVE) {
            frameSink.emitTickBatch(batch);
        }
    }

    private V31Frame buildFrame(MotionFilterCore core, PredictionModel.Estimate est,
                                V31Fix raw, Instant now) {
        String mode = est.mode();
        boolean offRoute = "OFF_ROUTE".equals(mode);
        RouteLine g = core.bank().leader().geom();
        double lat;
        double lon;
        Double course;
        String src;
        Double fraction = null;
        if (offRoute) {
            lat = raw.latitude();
            lon = raw.longitude();
            course = raw.course();
            src = "RAW";
        } else {
            double[] p = g.pointAtS(est.s());
            lat = p[0];
            lon = p[1];
            course = g.courseAt(est.s());
            src = "PRED";
            fraction = g.totalMeters() > 0 ? est.s() / g.totalMeters() : null;
        }
        double conf = 1.0 / (1.0 + core.positionVariance() / props.getPConfVarM2());
        boolean etaReliable = !UNRELIABLE_ETA_MODES.contains(mode);
        return new V31Frame(raw.vehicleId(), raw.licensePlate(), raw.routeNumber(),
                lat, lon, raw.speedKmh(), raw.inMotion(),
                now.toString(), "vehicle_position_update", course,
                offRoute ? null : core.direction(),
                !offRoute, fraction, conf,
                core.lastFixAt() != null ? core.lastFixAt().toString() : null,
                conf, mode, src, core.tripId(), now.toString(),
                etaReliable, offRoute ? Boolean.TRUE : null);
    }

    private boolean suppressUnchanged(String vehicleId, V31Frame frame, Instant now) {
        long qS = frame.fraction() != null ? Math.round(frame.fraction() * 1_000_000) : -1;
        long qConf = Math.round(frame.conf() / 0.05);
        SentState prev = lastSent.get(vehicleId);
        boolean unchanged = prev != null
                && prev.qS == qS
                && prev.mode.equals(frame.mode())
                && prev.src.equals(frame.src())
                && prev.tripSeq == frame.tripSeq()
                && prev.qConf == qConf
                && now.getEpochSecond() - prev.sentAt.getEpochSecond() < props.getTHeartbeatSec();
        if (unchanged) {
            framesSuppressed.incrementAndGet();
            return true;
        }
        if (prev != null && prev.tripSeq != frame.tripSeq()) {
            double jump = RouteLine.haversineMeters(prev.lat, prev.lon,
                    frame.latitude(), frame.longitude());
            if (jump > BOUNDARY_SANITY_CAP_METERS) {
                boundaryCapPrints.incrementAndGet();
                log.warn("W4-№14: скачок кадра на границе tripSeq {}→{} = {} м (> {} м)",
                        prev.tripSeq, frame.tripSeq(), Math.round(jump),
                        Math.round(BOUNDARY_SANITY_CAP_METERS));
            }
        }
        lastSent.put(vehicleId, new SentState(qS, frame.mode(), frame.src(), frame.tripSeq(),
                qConf, now, frame.latitude(), frame.longitude(), frame.tripSeq()));
        return false;
    }

    private synchronized void writeShadow(String json, Instant now) {
        try {
            String hour = HOUR.format(now);
            if (frameWriter == null || !hour.equals(writerHour)) {
                if (frameWriter != null) {
                    frameWriter.close();
                }
                Files.createDirectories(logDirectory);
                writerHour = hour;
                frameWriter = Files.newBufferedWriter(
                        logDirectory.resolve("ws_pred_v31_frames-" + hour + ".jsonl"));
            }
            frameWriter.write(json);
            frameWriter.write(System.lineSeparator());
            frameWriter.flush();
            framesWritten.incrementAndGet();
        } catch (IOException e) {
            shadowWriteErrors.incrementAndGet();
            log.debug("v31 frame shadow write failed: {}", e.getMessage());
        }
    }

    public long framesEmitted() {
        return framesEmitted.get();
    }

    public long framesSuppressed() {
        return framesSuppressed.get();
    }

    public long serializations() {
        return serializations.get();
    }

    public long boundaryCapPrints() {
        return boundaryCapPrints.get();
    }

    public long framesWritten() {
        return framesWritten.get();
    }

    public long shadowWriteErrors() {
        return shadowWriteErrors.get();
    }
}
