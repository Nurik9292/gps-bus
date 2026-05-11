package biz.ugur.busroutebackend.routing.infrastructure.services;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnExpression(
        "${routing.raptor.enabled:false} or ${routing.shadow-mode.enabled:false}")
@Slf4j
public class RoutingMetrics {

    public static final String ENGINE_DIJKSTRA = "dijkstra";
    public static final String ENGINE_RAPTOR = "raptor";

    public enum Operation { DIRECT, ONE_TRANSFER, TWO_TRANSFER, STOPS_CONNECTED, CONNECTING_ROUTES }

    public enum DiscrepancyType {
        ONLY_DIJKSTRA,
        ONLY_RAPTOR,
        DIFFERENT_ROUTES,
        DIFFERENT_DIRECTION,
        DIFFERENT_ETA,
        RAPTOR_ERROR
    }

    private final MeterRegistry registry;

    public RoutingMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordDuration(String engine, Operation operation, Duration elapsed) {
        Timer.builder("routing_calculation_duration_seconds")
                .tags(Tags.of("engine", engine, "operation", operation.name().toLowerCase()))
                .register(registry)
                .record(elapsed);
    }

    public void recordDiscrepancy(Operation operation, DiscrepancyType type) {
        Counter.builder("routing_results_diff_count")
                .tags(Tags.of(
                        "operation", operation.name().toLowerCase(),
                        "discrepancy_type", type.name().toLowerCase()))
                .register(registry)
                .increment();
    }

    public void recordRaptorError(Operation operation) {
        Counter.builder("raptor_errors_total")
                .tags(Tags.of("operation", operation.name().toLowerCase()))
                .register(registry)
                .increment();
    }
}
