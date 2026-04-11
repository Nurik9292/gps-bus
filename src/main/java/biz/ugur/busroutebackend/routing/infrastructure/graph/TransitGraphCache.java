package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;


@Component
@ConditionalOnProperty(name = "routing.dijkstra.enabled", havingValue = "true")
@Slf4j
@RequiredArgsConstructor
public class TransitGraphCache {

    private final TransitGraphBuilder builder;

    private static final Duration TTL = Duration.ofMinutes(30);

    private final AtomicReference<TransitGraph> graphRef = new AtomicReference<>();
    private volatile Mono<TransitGraph> buildingMono = null;

    @EventListener(ApplicationReadyEvent.class)
    public void preload() {
        log.info("🔄 Pre-loading transit graph at startup...");
        getGraph().subscribe(
                g -> log.info("✅ Transit graph ready: {} stops, {} edges", g.getStopCount(), g.getEdgeCount()),
                e -> log.error("❌ Failed to pre-load transit graph: {}", e.getMessage())
        );
    }

    public Mono<TransitGraph> getGraph() {
        TransitGraph existing = graphRef.get();
        if (existing != null && !existing.isExpired(TTL)) {
            return Mono.just(existing);
        }
        return rebuildGraph();
    }

    public void invalidate() {
        graphRef.set(null);
        log.info("🗑️  Transit graph cache invalidated");
    }

    private synchronized Mono<TransitGraph> rebuildGraph() {
        TransitGraph existing = graphRef.get();
        if (existing != null && !existing.isExpired(TTL)) {
            return Mono.just(existing);
        }

        if (buildingMono != null) {
            log.debug("Graph build already in progress, reusing shared Mono");
            return buildingMono;
        }

        log.info("♻️  Rebuilding transit graph (TTL expired or first load)...");
        buildingMono = builder.build()
                .doOnSuccess(graph -> {
                    graphRef.set(graph);
                    synchronized (TransitGraphCache.this) {
                        buildingMono = null;
                    }
                })
                .doOnError(e -> {
                    log.error("Failed to build transit graph: {}", e.getMessage());
                    synchronized (TransitGraphCache.this) {
                        buildingMono = null;
                    }
                })
                .cache(); 

        return buildingMono;
    }
}
