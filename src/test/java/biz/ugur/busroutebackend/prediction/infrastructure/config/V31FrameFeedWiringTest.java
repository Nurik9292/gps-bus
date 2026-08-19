package biz.ugur.busroutebackend.prediction.infrastructure.config;

import biz.ugur.busroutebackend.prediction.broadcast.V31BroadcastProperties;
import biz.ugur.busroutebackend.prediction.broadcast.V31FrameEnvelope;
import biz.ugur.busroutebackend.prediction.broadcast.V31FrameFeed;
import biz.ugur.busroutebackend.prediction.broadcast.V31FrameSink;
import biz.ugur.busroutebackend.transport.infrastructure.prediction.PredictionBroadcaster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class V31FrameFeedWiringTest {

    @Mock
    private V31FrameSink sink;
    @Mock
    private PredictionBroadcaster legacyBroadcaster;

    private final V31BroadcastConfig config = new V31BroadcastConfig();

    private static V31BroadcastProperties propertiesWith(V31BroadcastProperties.Mode mode) {
        V31BroadcastProperties props = new V31BroadcastProperties();
        props.setBroadcast(mode);
        return props;
    }

    private static void expectsNoFrames(V31FrameFeed feed) {
        StepVerifier.create(feed.frames().timeout(Duration.ofMillis(80)))
                .expectTimeout(Duration.ofMillis(120))
                .verify();
    }

    @Test
    void liveModeFeedsFramesFromSinkAndSuppressesLegacyBroadcast() {
        List<V31FrameEnvelope> batch = List.of();
        when(sink.asFlux()).thenReturn(Flux.just(batch));

        V31FrameFeed feed = config.v31FrameFeed(
                propertiesWith(V31BroadcastProperties.Mode.LIVE), sink, legacyBroadcaster);

        StepVerifier.create(feed.frames())
                .expectNext(batch)
                .verifyComplete();
        verify(legacyBroadcaster).v31LiveRouteSuppressor(any());
    }

    @Test
    void shadowModeStaysSilentAndLeavesLegacyBroadcastAlone() {
        expectsNoFrames(config.v31FrameFeed(
                propertiesWith(V31BroadcastProperties.Mode.SHADOW), sink, legacyBroadcaster));

        verify(legacyBroadcaster, never()).v31LiveRouteSuppressor(any());
    }

    @Test
    void offModeStaysSilentAndLeavesLegacyBroadcastAlone() {
        expectsNoFrames(config.v31FrameFeed(
                propertiesWith(V31BroadcastProperties.Mode.OFF), sink, legacyBroadcaster));

        verify(legacyBroadcaster, never()).v31LiveRouteSuppressor(any());
    }

    @Test
    void silentFeedNeverEmitsSoSocketsStayOpenWithoutV31() {
        expectsNoFrames(V31FrameFeed.silent());
    }
}
