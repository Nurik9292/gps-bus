package biz.ugur.busroutebackend.transport.infrastructure.debug;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class GpsRecordingCampaignTest {

    @TempDir
    Path tempDir;

    private static final Instant T0 = Instant.parse("2026-07-01T10:00:00Z");

    private GpsRecorder recorder;
    private GpsRecordingCampaign campaign;

    @BeforeEach
    void setUp() {
        GpsRecorderProperties props = new GpsRecorderProperties();
        props.setOutputDir(tempDir.toString());
        props.setMaxDurationSec(3600);
        props.setSessionDurationSec(3600);
        props.setSessionPrefix("campaign");
        recorder = new GpsRecorder(props, new ObjectMapper());
        campaign = new GpsRecordingCampaign(recorder, props, Clock.systemUTC());
    }

    private void recordOneFix(Instant at) {
        recorder.recordIfActive("veh-1", "1216 AGJ", "8",
                37.958, 58.353, 40.0, 92.0, true, at, 0, 1.2, 9, 5.0);
    }

    @Test
    void firstTickStartsNamedHourlySession() {
        campaign.tick(T0);

        assertThat(recorder.status().active()).isTrue();
        assertThat(recorder.status().scenario()).isEqualTo("campaign-20260701-1000");
        assertThat(Files.exists(tempDir.resolve("campaign-20260701-1000.jsonl"))).isTrue();
    }

    @Test
    void flushWithinSessionPersistsDataWithoutRolling() throws Exception {
        campaign.tick(T0);
        recordOneFix(T0);

        campaign.tick(T0.plusSeconds(1800));

        assertThat(recorder.status().scenario()).isEqualTo("campaign-20260701-1000");
        assertThat(Files.readAllLines(tempDir.resolve("campaign-20260701-1000.jsonl"))).hasSize(1);
    }

    @Test
    void rollsToNewNamedSessionAfterDurationKeepingPreviousCorpus() throws Exception {
        campaign.tick(T0);
        recordOneFix(T0);

        campaign.tick(T0.plusSeconds(3601));

        assertThat(recorder.status().scenario()).isEqualTo("campaign-20260701-1100");
        assertThat(Files.exists(tempDir.resolve("campaign-20260701-1100.jsonl"))).isTrue();
        assertThat(Files.readAllLines(tempDir.resolve("campaign-20260701-1000.jsonl"))).hasSize(1);
    }

    @Test
    void rollByClockHappensEvenWithoutFixesDuringFeedGap() {
        campaign.tick(T0);

        campaign.tick(T0.plusSeconds(3601));

        assertThat(recorder.status().scenario()).isEqualTo("campaign-20260701-1100");
        assertThat(Files.exists(tempDir.resolve("campaign-20260701-1000.jsonl"))).isTrue();
    }
}
