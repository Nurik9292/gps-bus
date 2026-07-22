package biz.ugur.busroutebackend.prediction.infrastructure.logrotation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Component
@Slf4j
public class V31LogJanitor {

    private static final String V31_FILE_PREFIX = "ws_pred_v31_";

    private final Path logDirectory;
    private final Duration retention;
    private final long capBytes;
    private final Clock clock;

    public V31LogJanitor(@Value("${app.prediction.v31.log-dir:logs/ws_pred_v31}") String logDir,
                         @Value("${app.prediction.v31.log-retention-hours:24}") int retentionHours,
                         @Value("${app.prediction.v31.log-cap-bytes:500000000}") long capBytes,
                         Clock clock) {
        this.logDirectory = Path.of(logDir);
        this.retention = Duration.ofHours(retentionHours);
        this.capBytes = capBytes;
        this.clock = clock;
    }

    @Scheduled(initialDelayString = "2", fixedRateString = "30", timeUnit = TimeUnit.MINUTES)
    public void scheduledSweep() {
        try {
            sweep(logDirectory, retention, capBytes, clock.instant());
        } catch (IOException | RuntimeException e) {
            log.warn("[V31_LOGS] уборка не удалась: {}", e.getMessage());
        }
    }

    static void sweep(Path directory, Duration retention, long capBytes, Instant now)
            throws IOException {
        if (!Files.isDirectory(directory)) {
            return;
        }
        record LogFile(Path path, Instant modifiedAt, long size) {
        }
        List<LogFile> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(directory)) {
            for (Path path : stream.toList()) {
                if (!Files.isRegularFile(path)
                        || !path.getFileName().toString().startsWith(V31_FILE_PREFIX)) {
                    continue;
                }
                files.add(new LogFile(path,
                        Files.getLastModifiedTime(path).toInstant(), Files.size(path)));
            }
        }
        files.sort(Comparator.comparing(LogFile::modifiedAt));

        long totalSize = files.stream().mapToLong(LogFile::size).sum();
        int deleted = 0;
        long freed = 0;
        Instant cutoff = now.minus(retention);
        for (LogFile file : files) {
            boolean tooOld = file.modifiedAt().isBefore(cutoff);
            boolean overCap = totalSize - freed > capBytes;
            if (!tooOld && !overCap) {
                break;
            }
            Files.deleteIfExists(file.path());
            deleted++;
            freed += file.size();
        }
        if (deleted > 0) {
            org.slf4j.LoggerFactory.getLogger(V31LogJanitor.class).info(
                    "[V31_LOGS] уборка: удалено {} файлов, освобождено {} МБ (осталось {} МБ)",
                    deleted, freed / 1_048_576, (totalSize - freed) / 1_048_576);
        }
    }
}
