package biz.ugur.busroutebackend.transport.domain.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FrozenCoordsRegistryTest {

    private static final Instant T0 = Instant.parse("2026-07-23T10:00:00Z");

    private MutableClock clock;
    private FrozenCoordsRegistry registry;

    private static final class MutableClock extends Clock {
        private Instant instant = T0;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        registry = new FrozenCoordsRegistry(Duration.ofMinutes(10), Duration.ofMinutes(10),
                Duration.ofMinutes(30), clock);
    }

    private FrozenCoordsRegistry.FrozenEpisode recordAt(Instant at) {
        clock.instant = at;
        return registry.recordFrozenEvent("dev-1", null, null, 30.0);
    }

    @Test
    void firstFrozenEventIsWarnAllowed() {
        var episode = recordAt(T0);
        assertThat(episode.warnAllowed()).isTrue();
        assertThat(episode.detectionCount()).isEqualTo(1);
        assertThat(episode.firstFrozenAt()).isEqualTo(T0);
    }

    @Test
    void repeatWithinDedupIntervalSuppressesWarn() {
        recordAt(T0);
        var second = recordAt(T0.plusSeconds(60));
        assertThat(second.warnAllowed()).isFalse();
        assertThat(second.detectionCount()).isEqualTo(2);
    }

    @Test
    void warnReallowedAfterDedupInterval() {
        recordAt(T0);
        var later = recordAt(T0.plus(Duration.ofMinutes(10)));
        assertThat(later.warnAllowed()).isTrue();
    }

    @Test
    void episodeSpanningChronicThresholdIsListedChronic() {
        recordAt(T0);
        recordAt(T0.plus(Duration.ofMinutes(11)));
        var chronic = registry.chronicallyFrozen();
        assertThat(chronic).hasSize(1);
        assertThat(chronic.get(0).deviceId()).isEqualTo("dev-1");
        assertThat(chronic.get(0).firstFrozenAt()).isEqualTo(T0);
    }

    @Test
    void freshEpisodeIsNotChronic() {
        recordAt(T0);
        assertThat(registry.chronicallyFrozen()).isEmpty();
    }

    @Test
    void coordinatesMovedEndsEpisode() {
        recordAt(T0);
        recordAt(T0.plus(Duration.ofMinutes(11)));
        registry.recordCoordinatesMoved("dev-1");
        assertThat(registry.chronicallyFrozen()).isEmpty();
    }

    @Test
    void coordinatesMovedDoesNotResetWarnSlot() {
        recordAt(T0);
        registry.recordCoordinatesMoved("dev-1");
        var again = recordAt(T0.plusSeconds(120));
        assertThat(again.warnAllowed()).isFalse();
    }

    @Test
    void silenceLongerThanRetentionRestartsEpisode() {
        recordAt(T0);
        var restarted = recordAt(T0.plus(Duration.ofMinutes(40)));
        assertThat(restarted.firstFrozenAt()).isEqualTo(T0.plus(Duration.ofMinutes(40)));
        assertThat(restarted.detectionCount()).isEqualTo(1);
        assertThat(registry.chronicallyFrozen()).isEmpty();
    }

    @Test
    void plateArrivingLaterEnrichesEpisode() {
        recordAt(T0);
        clock.instant = T0.plusSeconds(30);
        var enriched = registry.recordFrozenEvent("dev-1", "1315 AGJ", "110", 25.0);
        assertThat(enriched.licensePlate()).isEqualTo("1315 AGJ");
        assertThat(enriched.routeNumber()).isEqualTo("110");
    }
}
