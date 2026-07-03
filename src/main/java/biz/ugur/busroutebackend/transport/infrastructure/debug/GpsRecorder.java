package biz.ugur.busroutebackend.transport.infrastructure.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "gps.recorder", name = "enabled", havingValue = "true")
public class GpsRecorder {

    private final GpsRecorderProperties properties;
    private final ObjectMapper objectMapper;

    private final AtomicReference<ActiveSession> activeSession = new AtomicReference<>();

    public GpsRecorder(GpsRecorderProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public synchronized StartResult start(String scenario, int durationSec) {
        if (activeSession.get() != null) {
            return new StartResult(false, "recording already active",
                    activeSession.get().scenario, null);
        }
        int capped = Math.min(durationSec, properties.getMaxDurationSec());
        Path outputPath = Paths.get(properties.getOutputDir(), scenario + ".jsonl");

        try {
            Files.createDirectories(outputPath.getParent());
            BufferedWriter writer = Files.newBufferedWriter(outputPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            Instant startedAt = Instant.now();
            Instant stopAt = startedAt.plusSeconds(capped);
            activeSession.set(new ActiveSession(scenario, startedAt, stopAt, writer, outputPath));
            log.info("[GPS_RECORDER] START scenario={} duration={}s output={}",
                    scenario, capped, outputPath);
            return new StartResult(true, null, scenario, outputPath.toString());
        } catch (IOException e) {
            log.error("[GPS_RECORDER] Failed to start scenario={}: {}", scenario, e.getMessage());
            return new StartResult(false, "failed to open output file: " + e.getMessage(),
                    scenario, outputPath.toString());
        }
    }

    public synchronized StopResult stop() {
        ActiveSession session = activeSession.getAndSet(null);
        if (session == null) {
            return new StopResult(false, "no active recording", null, 0, 0);
        }
        closeQuietly(session);
        long recordedCount = session.recordedCount;
        long elapsedMs = Duration.between(session.startedAt, Instant.now()).toMillis();
        log.info("[GPS_RECORDER] STOP scenario={} recorded={} elapsed={}ms",
                session.scenario, recordedCount, elapsedMs);
        return new StopResult(true, null, session.scenario, recordedCount, elapsedMs);
    }

    public void recordIfActive(String vehicleId,
                                String licensePlate,
                                String routeNumber,
                                double latitude,
                                double longitude,
                                double speedKmh,
                                double course,
                                boolean inMotion,
                                Instant timestamp,
                                int direction,
                                Double hdop,
                                Integer satellites,
                                Double accuracy) {
        ActiveSession session = activeSession.get();
        if (session == null) return;

        if (!routePassesFilter(routeNumber)) return;

        Instant now = Instant.now();
        if (now.isAfter(session.stopAt)) {
            stop();
            return;
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("vehicleId", vehicleId);
        event.put("licensePlate", licensePlate);
        event.put("routeNumber", routeNumber);
        event.put("latitude", latitude);
        event.put("longitude", longitude);
        event.put("speedKmh", speedKmh);
        event.put("course", course);
        event.put("inMotion", inMotion);
        event.put("timestamp", timestamp.toString());
        event.put("direction", direction);
        event.put("hdop", hdop);
        event.put("satellites", satellites);
        event.put("accuracy", accuracy);
        event.put("wallClock", now.toString());

        try {
            String line = objectMapper.writeValueAsString(event);
            synchronized (session.writer) {
                session.writer.write(line);
                session.writer.newLine();
                session.recordedCount++;
            }
        } catch (IOException e) {
            log.warn("[GPS_RECORDER] failed to write event: {}", e.getMessage());
        }
    }

    private boolean routePassesFilter(String routeNumber) {
        java.util.List<String> routes = properties.getRoutes();
        if (routes == null || routes.isEmpty()) return true;
        return routeNumber != null && routes.contains(routeNumber);
    }

    public void flush() {
        ActiveSession session = activeSession.get();
        if (session == null) return;
        try {
            synchronized (session.writer) {
                session.writer.flush();
            }
        } catch (IOException e) {
            log.warn("[GPS_RECORDER] flush failed: {}", e.getMessage());
        }
    }

    public RecorderStatus status() {
        ActiveSession session = activeSession.get();
        if (session == null) {
            return new RecorderStatus(false, null, 0, 0, null);
        }
        long elapsedMs = Duration.between(session.startedAt, Instant.now()).toMillis();
        long remainingMs = Math.max(0, Duration.between(Instant.now(), session.stopAt).toMillis());
        return new RecorderStatus(true, session.scenario, session.recordedCount,
                remainingMs, session.outputPath.toString());
    }

    @PreDestroy
    public void shutdown() {
        ActiveSession session = activeSession.getAndSet(null);
        if (session != null) {
            closeQuietly(session);
            log.info("[GPS_RECORDER] SHUTDOWN scenario={} recorded={}",
                    session.scenario, session.recordedCount);
        }
    }

    private void closeQuietly(ActiveSession session) {
        try {
            session.writer.flush();
            session.writer.close();
        } catch (IOException e) {
            log.warn("[GPS_RECORDER] close failed: {}", e.getMessage());
        }
    }

    private static final class ActiveSession {
        final String scenario;
        final Instant startedAt;
        final Instant stopAt;
        final BufferedWriter writer;
        final Path outputPath;
        volatile long recordedCount = 0;

        ActiveSession(String scenario, Instant startedAt, Instant stopAt,
                      BufferedWriter writer, Path outputPath) {
            this.scenario = scenario;
            this.startedAt = startedAt;
            this.stopAt = stopAt;
            this.writer = writer;
            this.outputPath = outputPath;
        }
    }

    public record StartResult(boolean started, String error, String scenario, String outputPath) {}
    public record StopResult(boolean stopped, String error, String scenario, long recordedCount, long elapsedMs) {}
    public record RecorderStatus(boolean active, String scenario, long recordedCount, long remainingMs, String outputPath) {}
}
