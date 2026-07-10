package biz.ugur.busroutebackend.prediction.shadow;

import biz.ugur.busroutebackend.prediction.core.CoreConfig;
import biz.ugur.busroutebackend.prediction.core.GpsFix;
import biz.ugur.busroutebackend.prediction.core.MotionFilterCore;
import biz.ugur.busroutebackend.prediction.core.PredictionModel;
import biz.ugur.busroutebackend.prediction.core.RouteTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.Disposable;
import reactor.core.scheduler.Schedulers;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class V31ShadowService {

    private static final Logger log = LoggerFactory.getLogger(V31ShadowService.class);

    private final V31RouteLines routeLines;
    private final Clock clock;
    private final Path fixLogPath;
    private final Path tickLogPath;
    private final Map<String, MotionFilterCore> cores = new ConcurrentHashMap<>();
    private final AtomicLong v31TicksProcessed = new AtomicLong();
    private final AtomicLong v31ErrorCount = new AtomicLong();
    private BufferedWriter fixWriter;
    private BufferedWriter tickWriter;
    private Disposable subscription;

    public V31ShadowService(V31ShadowTap tap, V31RouteLines routeLines, Clock clock,
                            Path logDirectory) {
        this.routeLines = routeLines;
        this.clock = clock;
        this.fixLogPath = logDirectory.resolve("ws_pred_v31_fixes.jsonl");
        this.tickLogPath = logDirectory.resolve("ws_pred_v31_ticks.psv");
        this.subscription = tap.flux()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(fix -> {
                    try {
                        process(fix);
                    } catch (Exception e) {
                        v31ErrorCount.incrementAndGet();
                        log.debug("v31 shadow tick failed: {}", e.getMessage());
                    }
                }, err -> {
                    v31ErrorCount.incrementAndGet();
                    log.warn("v31 shadow stream terminated: {}", err.getMessage());
                });
    }

    void process(V31Fix fix) {
        RouteTopology topo = routeLines.topologyFor(fix.routeNumber());
        if (topo == null) return;
        MotionFilterCore core = cores.computeIfAbsent(fix.vehicleId(), id -> {
            MotionFilterCore c = new MotionFilterCore(CoreConfig.defaults());
            c.reset();
            return c;
        });
        GpsFix gpsFix = new GpsFix(fix.vehicleId(), fix.licensePlate(), fix.routeNumber(),
                fix.latitude(), fix.longitude(), fix.speedKmh(), fix.course(),
                fix.inMotion(), fix.timestamp(), fix.direction(), null, null, null, null);
        PredictionModel.Estimate est = core.onFix(gpsFix, topo);
        writeLogs(fix, core, est);
        v31TicksProcessed.incrementAndGet();
    }

    private synchronized void writeLogs(V31Fix fix, MotionFilterCore core,
                                        PredictionModel.Estimate est) {
        try {
            if (fixWriter == null) {
                Files.createDirectories(fixLogPath.getParent());
                fixWriter = Files.newBufferedWriter(fixLogPath);
                tickWriter = Files.newBufferedWriter(tickLogPath);
                tickWriter.write("vid8|ts|mode|leader|s\n");
            }
            fixWriter.write(String.format(Locale.ROOT,
                    "{\"vehicleId\":\"%s\",\"licensePlate\":\"%s\",\"routeNumber\":\"%s\","
                            + "\"latitude\":%.7f,\"longitude\":%.7f,\"speedKmh\":%.4f,"
                            + "\"course\":%.1f,\"inMotion\":%s,\"timestamp\":\"%s\","
                            + "\"direction\":%d}%n",
                    fix.vehicleId(), fix.licensePlate(), fix.routeNumber(), fix.latitude(),
                    fix.longitude(), fix.speedKmh(), fix.course(), fix.inMotion(),
                    fix.timestamp(), fix.direction()));
            tickWriter.write(String.format(Locale.ROOT, "%s|%d|%s|%s|%.1f%n",
                    fix.vehicleId().length() >= 8 ? fix.vehicleId().substring(0, 8) : fix.vehicleId(),
                    fix.timestamp().toEpochMilli(), est.mode(),
                    core.bank().leader().variantId(), est.s()));
            fixWriter.flush();
            tickWriter.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public long v31TicksProcessed() {
        return v31TicksProcessed.get();
    }

    public long v31ErrorCount() {
        return v31ErrorCount.get();
    }

    public Clock clock() {
        return clock;
    }

    public void shutdown() {
        if (subscription != null) subscription.dispose();
        try {
            if (fixWriter != null) fixWriter.close();
            if (tickWriter != null) tickWriter.close();
        } catch (IOException e) {
            log.debug("v31 shadow log close failed: {}", e.getMessage());
        }
    }
}
