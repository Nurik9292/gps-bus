package biz.ugur.busroutebackend.prediction.broadcast;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.List;

public class V31FrameSink {

    private final Sinks.Many<List<V31FrameEnvelope>> sink =
            Sinks.many().multicast().directBestEffort();

    public Flux<List<V31FrameEnvelope>> asFlux() {
        return sink.asFlux();
    }

    public void emitTickBatch(List<V31FrameEnvelope> batch) {
        if (!batch.isEmpty()) {
            sink.tryEmitNext(batch);
        }
    }
}
