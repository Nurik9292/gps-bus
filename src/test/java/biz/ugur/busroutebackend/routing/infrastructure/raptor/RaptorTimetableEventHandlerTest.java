package biz.ugur.busroutebackend.routing.infrastructure.raptor;

import biz.ugur.busroutebackend.shared.infrastructure.messaging.ReactiveEventBus;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RaptorTimetableEventHandlerTest {

    private final ReactiveEventBus eventBus = mock(ReactiveEventBus.class);
    private final RaptorTimetableCache cache = mock(RaptorTimetableCache.class);
    private final RaptorTimetableGenerator timetableGenerator = mock(RaptorTimetableGenerator.class);
    private final RaptorTransferGenerator transferGenerator = mock(RaptorTransferGenerator.class);

    private final RaptorTimetableEventHandler handler = new RaptorTimetableEventHandler(
            eventBus, cache, timetableGenerator, transferGenerator, 0);

    @Test
    void regeneratesTimetableAndTransfersThenInvalidatesCache() {
        when(timetableGenerator.regenerate())
                .thenReturn(Mono.just(new RaptorTimetableGenerator.GenerationResult(10, 100, 0)));
        when(transferGenerator.regenerate())
                .thenReturn(Mono.just(new RaptorTransferGenerator.GenerationResult(50, 25)));

        StepVerifier.create(handler.regenerateAndInvalidate("RouteGeometryUpdated: 14"))
                .verifyComplete();

        verify(timetableGenerator).regenerate();
        verify(transferGenerator).regenerate();
        verify(cache).invalidate();
    }

    @Test
    void doesNotInvalidateCacheWhenRegenerationFails() {
        when(timetableGenerator.regenerate())
                .thenReturn(Mono.error(new RuntimeException("regen failed")));

        StepVerifier.create(handler.regenerateAndInvalidate("RouteGeometryUpdated: 14"))
                .verifyComplete();

        verify(cache, never()).invalidate();
    }
}
