package biz.ugur.busroutebackend.prediction.shadow;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.concurrent.atomic.AtomicLong;

public class V31ShadowTap {

    private final Sinks.Many<V31Fix> sink = Sinks.many().multicast().directBestEffort();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong errors = new AtomicLong();

    public void accept(V31Fix fix) {
        Sinks.EmitResult result = sink.tryEmitNext(fix);
        if (result.isFailure()) {
            dropped.incrementAndGet();
        }
    }

    public Flux<V31Fix> flux() {
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
