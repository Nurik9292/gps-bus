package biz.ugur.busroutebackend.transport.infrastructure.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GpsRecorderTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private GpsRecorder recorderWritingTo(Path dir) {
        GpsRecorderProperties props = new GpsRecorderProperties();
        props.setOutputDir(dir.toString());
        props.setMaxDurationSec(300);
        return new GpsRecorder(props, objectMapper);
    }

    private Map<String, Object> firstRecordedEvent(String scenario) throws Exception {
        List<String> lines = Files.readAllLines(tempDir.resolve(scenario + ".jsonl"));
        assertThat(lines).isNotEmpty();
        return objectMapper.readValue(lines.get(0), Map.class);
    }

    @Test
    void recordsGpsQualityFieldsAlongsidePosition() throws Exception {
        GpsRecorder recorder = recorderWritingTo(tempDir);
        recorder.start("quality", 60);

        recorder.recordIfActive("veh-1", "1216 AGJ", "8",
                37.958, 58.353, 40.0, 92.0, true,
                Instant.parse("2026-06-30T10:00:00Z"), 0,
                1.2, 9, 5.0);

        recorder.stop();

        Map<String, Object> event = firstRecordedEvent("quality");
        assertThat(event).containsEntry("hdop", 1.2);
        assertThat(event).containsEntry("satellites", 9);
        assertThat(event).containsEntry("accuracy", 5.0);
        assertThat(event).containsEntry("latitude", 37.958);
    }

    @Test
    void missingQualityIsWrittenAsNullNotDropped() throws Exception {
        GpsRecorder recorder = recorderWritingTo(tempDir);
        recorder.start("noquality", 60);

        recorder.recordIfActive("veh-2", "1220 AGJ", "8",
                37.9, 58.3, 20.0, 90.0, true,
                Instant.parse("2026-06-30T10:00:00Z"), 0,
                null, null, null);

        recorder.stop();

        Map<String, Object> event = firstRecordedEvent("noquality");
        assertThat(event).containsKey("hdop");
        assertThat(event.get("hdop")).isNull();
        assertThat(event).containsKey("satellites");
        assertThat(event.get("satellites")).isNull();
        assertThat(event).containsKey("accuracy");
        assertThat(event.get("accuracy")).isNull();
    }
}
