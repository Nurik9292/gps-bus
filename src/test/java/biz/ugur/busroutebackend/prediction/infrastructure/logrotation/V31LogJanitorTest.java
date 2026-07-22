package biz.ugur.busroutebackend.prediction.infrastructure.logrotation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class V31LogJanitorTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    private static Path file(Path dir, String name, int sizeBytes, Instant modifiedAt)
            throws IOException {
        Path path = dir.resolve(name);
        Files.write(path, new byte[sizeBytes]);
        Files.setLastModifiedTime(path, FileTime.from(modifiedAt));
        return path;
    }

    @Test
    void deletesFilesOlderThanRetention(@TempDir Path dir) throws IOException {
        Path old = file(dir, "ws_pred_v31_frames-old.jsonl", 100, NOW.minus(Duration.ofHours(30)));
        Path fresh = file(dir, "ws_pred_v31_frames-new.jsonl", 100, NOW.minus(Duration.ofHours(2)));

        V31LogJanitor.sweep(dir, Duration.ofHours(24), 1_000_000, NOW);

        assertThat(old).doesNotExist();
        assertThat(fresh).exists();
    }

    @Test
    void enforcesSizeCapDeletingOldestFirst(@TempDir Path dir) throws IOException {
        Path oldest = file(dir, "ws_pred_v31_frames-a.jsonl", 400, NOW.minus(Duration.ofHours(3)));
        Path middle = file(dir, "ws_pred_v31_frames-b.jsonl", 400, NOW.minus(Duration.ofHours(2)));
        Path newest = file(dir, "ws_pred_v31_frames-c.jsonl", 400, NOW.minus(Duration.ofHours(1)));

        V31LogJanitor.sweep(dir, Duration.ofHours(24), 900, NOW);

        assertThat(oldest).doesNotExist();
        assertThat(middle).exists();
        assertThat(newest).exists();
    }

    @Test
    void foreignFilesAreNeverTouched(@TempDir Path dir) throws IOException {
        Path foreign = file(dir, "important.txt", 5_000, NOW.minus(Duration.ofDays(30)));
        Path v31old = file(dir, "ws_pred_v31_ticks-x.psv", 100, NOW.minus(Duration.ofDays(30)));

        V31LogJanitor.sweep(dir, Duration.ofHours(24), 10, NOW);

        assertThat(foreign).exists();
        assertThat(v31old).doesNotExist();
    }

    @Test
    void missingDirectoryIsNoop() throws IOException {
        V31LogJanitor.sweep(Path.of("/nonexistent-v31-logs"), Duration.ofHours(24), 10, NOW);
    }
}
