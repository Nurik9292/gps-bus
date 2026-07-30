package biz.ugur.busroutebackend.transport.infrastructure.monitoring.routeswap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicLong;

public class RouteSwapTap {

    private final Sinks.Many<RouteSwapFix> sink = Sinks.many().multicast().directBestEffort();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public void accept(RouteSwapFix fix) {
        Sinks.EmitResult result = sink.tryEmitNext(fix);
        if (result.isFailure()) {
            dropped.incrementAndGet();
        }
    }

    public Flux<RouteSwapFix> flux() {
        return sink.asFlux();
    }

    public long droppedCount() {
        return dropped.get();
    }

    public void recordError() {
        errors.incrementAndGet();
    }

    public long errorCount() {
        return errors.get();
    }
}
