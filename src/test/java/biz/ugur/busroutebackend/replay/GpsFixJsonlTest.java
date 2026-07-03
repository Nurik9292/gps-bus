package biz.ugur.busroutebackend.replay;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GpsFixJsonlTest {

    @TempDir
    Path tempDir;

    private static final String RECORDER_SAMPLE_WITH_QUALITY =
            "{\"vehicleId\":\"veh-1\",\"licensePlate\":\"1216 AGJ\",\"routeNumber\":\"8\","
            + "\"latitude\":37.958,\"longitude\":58.353,\"speedKmh\":40.0,\"course\":92.0,"
            + "\"inMotion\":true,\"timestamp\":\"2026-06-30T10:00:00Z\",\"direction\":0,"
            + "\"hdop\":1.2,\"satellites\":9,\"accuracy\":5.0,"
            + "\"wallClock\":\"2026-06-30T10:00:04.123Z\"}";

    private static final String LEGACY_SAMPLE_WITHOUT_QUALITY =
            "{\"vehicleId\":\"82753da3\",\"licensePlate\":\"1323 AGH\",\"routeNumber\":null,"
            + "\"latitude\":37.9866483,\"longitude\":58.3274716,\"speedKmh\":26.0,"
            + "\"course\":201.7,\"inMotion\":true,\"timestamp\":\"2026-04-18T07:37:31Z\","
            + "\"direction\":0,\"wallClock\":\"2026-04-18T07:37:52.144Z\"}";

    @Test
    void roundTripPreservesAllRecorderFields() throws Exception {
        Path in = tempDir.resolve("in.jsonl");
        Files.writeString(in, RECORDER_SAMPLE_WITH_QUALITY + "\n");

        List<GpsFix> fixes = GpsFixJsonl.read(in);
        assertThat(fixes).hasSize(1);
        GpsFix f = fixes.get(0);
        assertThat(f.vehicleId()).isEqualTo("veh-1");
        assertThat(f.licensePlate()).isEqualTo("1216 AGJ");
        assertThat(f.routeNumber()).isEqualTo("8");
        assertThat(f.latitude()).isEqualTo(37.958);
        assertThat(f.longitude()).isEqualTo(58.353);
        assertThat(f.speedKmh()).isEqualTo(40.0);
        assertThat(f.course()).isEqualTo(92.0);
        assertThat(f.inMotion()).isTrue();
        assertThat(f.timestamp()).isEqualTo("2026-06-30T10:00:00Z");
        assertThat(f.direction()).isEqualTo(0);
        assertThat(f.hdop()).isEqualTo(1.2);
        assertThat(f.satellites()).isEqualTo(9);
        assertThat(f.accuracy()).isEqualTo(5.0);
        assertThat(f.wallClock()).isEqualTo("2026-06-30T10:00:04.123Z");

        Path out = tempDir.resolve("out.jsonl");
        GpsFixJsonl.write(out, fixes);
        List<GpsFix> reread = GpsFixJsonl.read(out);
        assertThat(reread).isEqualTo(fixes);
    }

    @Test
    void legacyLinesWithoutQualityReadAsNullQuality() throws Exception {
        Path in = tempDir.resolve("legacy.jsonl");
        Files.writeString(in, LEGACY_SAMPLE_WITHOUT_QUALITY + "\n");

        GpsFix f = GpsFixJsonl.read(in).get(0);
        assertThat(f.routeNumber()).isNull();
        assertThat(f.hdop()).isNull();
        assertThat(f.satellites()).isNull();
        assertThat(f.accuracy()).isNull();
    }
}
