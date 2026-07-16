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
    private final Map<String, V31Fix> lastFixes = new ConcurrentHashMap<>();
    private java.util.function.Function<String, CoreConfig> configForRoute =
            r -> CoreConfig.defaults();
    private final AtomicLong v31TicksProcessed = new AtomicLong();
    private final AtomicLong v31LogDroppedTicks = new AtomicLong();
    private final V31ShadowTap tap;
    private final Path logDirectory;
    private final long logCapBytes;
    private long bytesWritten;
    private String writerHour;
    private BufferedWriter fixWriter;
    private BufferedWriter tickWriter;
    private Disposable subscription;

    public V31ShadowService(V31ShadowTap tap, V31RouteLines routeLines, Clock clock,
                            Path logDirectory) {
        this(tap, routeLines, clock, logDirectory, 500_000_000L);
    }

    public V31ShadowService(V31ShadowTap tap, V31RouteLines routeLines, Clock clock,
                            Path logDirectory, long logCapBytes) {
        this.tap = tap;
        this.routeLines = routeLines;
        this.clock = clock;
        this.logDirectory = logDirectory;
        this.logCapBytes = logCapBytes;
        this.fixLogPath = logDirectory.resolve("ws_pred_v31_fixes.jsonl");
        this.tickLogPath = logDirectory.resolve("ws_pred_v31_ticks.psv");
        this.subscription = tap.flux()
                .publishOn(Schedulers.boundedElastic())
                .subscribe(fix -> {
                    try {
                        process(fix);
                    } catch (Exception e) {
                        tap.recordError();
                        log.debug("v31 shadow tick failed: {}", e.getMessage());
                    }
                }, err -> {
                    tap.recordError();
                    log.warn("v31 shadow stream terminated: {}", err.getMessage());
                });
    }

    public void processForReplay(V31Fix fix) {
        process(fix);
    }

    void process(V31Fix fix) {
        RouteTopology topo = routeLines.topologyFor(fix.routeCacheKey(), fix.routeNumber());
        if (topo == null) return;
        MotionFilterCore core = cores.computeIfAbsent(fix.vehicleId(), id -> {
            MotionFilterCore c = new MotionFilterCore(configForRoute.apply(fix.routeNumber()));
            c.reset();
            return c;
        });
        lastFixes.put(fix.vehicleId(), fix);
        GpsFix gpsFix = new GpsFix(fix.vehicleId(), fix.licensePlate(), fix.routeNumber(),
                fix.latitude(), fix.longitude(), fix.speedKmh(), fix.course(),
                fix.inMotion(), fix.timestamp(), fix.direction(), fix.hdop(), fix.satellites(), fix.accuracy(), null);
        PredictionModel.Estimate est;
        synchronized (core) {
            est = core.onFix(gpsFix, topo);
        }
        writeLogs(fix, core, est);
        v31TicksProcessed.incrementAndGet();
    }

    private synchronized void writeLogs(V31Fix fix, MotionFilterCore core,
                                        PredictionModel.Estimate est) {
        try {
            if (bytesWritten >= logCapBytes) {
                v31LogDroppedTicks.incrementAndGet();
                return;
            }
            String hour = java.time.ZonedDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC)
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HH"));
            if (fixWriter == null || !hour.equals(writerHour)) {
                if (fixWriter != null) {
                    fixWriter.close();
                    tickWriter.close();
                }
                Files.createDirectories(logDirectory);
                if (writerHour == null) {
                    try (var files = Files.list(logDirectory)) {
                        bytesWritten = files.filter(Files::isRegularFile)
                                .mapToLong(f -> f.toFile().length()).sum();
                    }
                }
                writerHour = hour;
                fixWriter = Files.newBufferedWriter(logDirectory.resolve(
                        "ws_pred_v31_fixes-" + hour + ".jsonl"));
                tickWriter = Files.newBufferedWriter(logDirectory.resolve(
                        "ws_pred_v31_ticks-" + hour + ".psv"));
                tickWriter.write("vid8|ts|mode|leader|s\n");
            }
            String fixLine = String.format(Locale.ROOT,
                    "{\"vehicleId\":\"%s\",\"licensePlate\":\"%s\",\"routeNumber\":\"%s\","
                            + "\"latitude\":%.7f,\"longitude\":%.7f,\"speedKmh\":%.4f,"
                            + "\"course\":%.1f,\"inMotion\":%s,\"timestamp\":\"%s\","
                            + "\"direction\":%d}%n",
                    fix.vehicleId(), fix.licensePlate(), fix.routeNumber(), fix.latitude(),
                    fix.longitude(), fix.speedKmh(), fix.course(), fix.inMotion(),
                    fix.timestamp(), fix.direction());
            String tickLine = String.format(Locale.ROOT, "%s|%d|%s|%s|%.1f%n",
                    fix.vehicleId().length() >= 8 ? fix.vehicleId().substring(0, 8) : fix.vehicleId(),
                    fix.timestamp().toEpochMilli(), est.mode(),
                    core.bank().leader().variantId(), est.s());
            fixWriter.write(fixLine);
            tickWriter.write(tickLine);
            bytesWritten += fixLine.getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                    + tickLine.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            fixWriter.flush();
            tickWriter.flush();
        } catch (IOException | RuntimeException e) {
            v31LogDroppedTicks.incrementAndGet();
            log.debug("v31 shadow log write failed: {}", e.getMessage());
        }
    }

    public long v31TicksProcessed() {
        return v31TicksProcessed.get();
    }

    public long v31ErrorCount() {
        return tap.errorCount();
    }

    public long v31LogDroppedTicks() {
        return v31LogDroppedTicks.get();
    }

    public Clock clock() {
        return clock;
    }

    public Map<String, MotionFilterCore> coresView() {
        return java.util.Collections.unmodifiableMap(cores);
    }

    public V31Fix lastFixOf(String vehicleId) {
        return lastFixes.get(vehicleId);
    }

    public void configForRoute(java.util.function.Function<String, CoreConfig> fn) {
        this.configForRoute = fn;
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
