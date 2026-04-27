package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionProperties;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.VehiclePredictionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class DirectionChangeCooldownTest {

    private PredictionProperties props;
    private DirectionChangeCooldown cooldown;

    @BeforeEach
    void setUp() {
        props = new PredictionProperties();
        cooldown = new DirectionChangeCooldown(props);
    }

    @Test
    void inactiveWhenStateNull() {
        assertThat(cooldown.isActive(null)).isFalse();
        assertThat(cooldown.ageMs(null)).isEqualTo(-1);
    }

    @Test
    void inactiveWhenDirectionChangeMissing() {
        VehiclePredictionState s = VehiclePredictionState.builder().vehicleId("v").build();
        assertThat(cooldown.isActive(s)).isFalse();
        assertThat(cooldown.ageMs(s)).isEqualTo(-1);
    }

    @Test
    void activeWhenWithinCooldownWindow() {
        VehiclePredictionState s = VehiclePredictionState.builder()
                .vehicleId("v")
                .directionChangedAt(Instant.now().minusMillis(1_000))
                .build();
        assertThat(cooldown.isActive(s)).isTrue();
        assertThat(cooldown.ageMs(s)).isBetween(900L, 2_000L);
    }

    @Test
    void inactiveAfterCooldownExpires() {
        VehiclePredictionState s = VehiclePredictionState.builder()
                .vehicleId("v")
                .directionChangedAt(Instant.now().minusMillis(props.getDirChangeCooldownMs() + 1_000))
                .build();
        assertThat(cooldown.isActive(s)).isFalse();
    }
}
