package biz.ugur.busroutebackend.transport.infrastructure.prediction.snap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConsecutiveOppositeCounterTest {

    private ConsecutiveOppositeCounter counter;

    @BeforeEach
    void setUp() {
        counter = new ConsecutiveOppositeCounter();
    }

    @Test
    void incrementGrowsThenResetsToZero() {
        assertThat(counter.incrementAndGet("v1")).isEqualTo(1);
        assertThat(counter.incrementAndGet("v1")).isEqualTo(2);
        assertThat(counter.incrementAndGet("v1")).isEqualTo(3);
        counter.reset("v1");
        assertThat(counter.incrementAndGet("v1")).isEqualTo(1);
    }

    @Test
    void perVehicleCountersIndependent() {
        counter.incrementAndGet("v1");
        counter.incrementAndGet("v1");
        counter.incrementAndGet("v2");
        assertThat(counter.incrementAndGet("v1")).isEqualTo(3);
        assertThat(counter.incrementAndGet("v2")).isEqualTo(2);
    }

    @Test
    void drainPendingDirectionFixesReturnsAndClears() {
        counter.queueDirectionFix("v1", 1);
        counter.queueDirectionFix("v2", 0);

        Map<String, Integer> first = counter.drainPendingDirectionFixes();
        assertThat(first).containsEntry("v1", 1).containsEntry("v2", 0);

        Map<String, Integer> second = counter.drainPendingDirectionFixes();
        assertThat(second).isEmpty();
    }

    @Test
    void retainOnlyRemovesEvictedVehicleCounters() {
        counter.incrementAndGet("alive");
        counter.incrementAndGet("dead");
        counter.retainOnly(Set.of("alive"));

        assertThat(counter.incrementAndGet("alive")).isEqualTo(2);
        assertThat(counter.incrementAndGet("dead")).isEqualTo(1);
    }
}
