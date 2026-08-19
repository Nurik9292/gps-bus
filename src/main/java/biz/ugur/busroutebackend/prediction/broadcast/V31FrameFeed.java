package biz.ugur.busroutebackend.prediction.broadcast;

import reactor.core.publisher.Flux;

import java.util.List;

@FunctionalInterface
public interface V31FrameFeed {

    Flux<List<V31FrameEnvelope>> frames();

    static V31FrameFeed silent() {
        return Flux::never;
    }
}
