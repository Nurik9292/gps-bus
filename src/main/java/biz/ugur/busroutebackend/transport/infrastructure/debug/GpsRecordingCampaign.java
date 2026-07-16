package biz.ugur.busroutebackend.transport.infrastructure.debug;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "gps.recorder", name = "campaign-enabled", havingValue = "true")
public class GpsRecordingCampaign {

    private static final DateTimeFormatter SESSION_STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").withZone(ZoneOffset.UTC);

    private final GpsRecorder recorder;
    private final GpsRecorderProperties properties;
    private final Clock clock;

    private final AtomicReference<Instant> sessionStartedAt = new AtomicReference<>();

    public GpsRecordingCampaign(GpsRecorder recorder, GpsRecorderProperties properties, Clock clock) {
        this.recorder = recorder;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gps.recorder.roll-check-interval-sec:30}", timeUnit = TimeUnit.SECONDS)
    void roll() {
        tick(clock.instant());
    }

    void tick(Instant now) {
        Instant startedAt = sessionStartedAt.get();
        if (startedAt == null) {
            startSession(now);
            return;
        }
        if (Duration.between(startedAt, now).getSeconds() >= properties.getSessionDurationSec()) {
            recorder.stop();
            startSession(now);
            return;
        }
        recorder.flush();
    }

    private void startSession(Instant now) {
        String scenario = properties.getSessionPrefix() + "-" + SESSION_STAMP.format(now);
        recorder.start(scenario, properties.getSessionDurationSec());
        sessionStartedAt.set(now);
        log.info("[GPS_RECORDER] CAMPAIGN_SESSION_STARTED scenario={} durationSec={}",
                scenario, properties.getSessionDurationSec());
    }
}
