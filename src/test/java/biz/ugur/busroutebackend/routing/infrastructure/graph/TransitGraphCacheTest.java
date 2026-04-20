package biz.ugur.busroutebackend.routing.infrastructure.graph;

import biz.ugur.busroutebackend.routing.domain.model.graph.TransitGraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class TransitGraphCacheTest {

    @InjectMocks
    private TransitGraphCache cache;

    @Mock
    private TransitGraphBuilder builder;

    @Test
    void getGraphBuildsOnFirstCallAndCachesOnSecond() {
        TransitGraph graph = new TransitGraph(Map.of(), Map.of(), Map.of());
        when(builder.build()).thenReturn(Mono.just(graph));

        StepVerifier.create(cache.getGraph())
                .expectNext(graph)
                .verifyComplete();

        StepVerifier.create(cache.getGraph())
                .expectNext(graph)
                .verifyComplete();

        verify(builder, times(1)).build();
    }

    @Test
    void invalidateForcesRebuildOnNextCall() {
        TransitGraph first = new TransitGraph(Map.of(), Map.of(), Map.of());
        TransitGraph second = new TransitGraph(Map.of(), Map.of(), Map.of());
        when(builder.build()).thenReturn(Mono.just(first), Mono.just(second));

        StepVerifier.create(cache.getGraph()).expectNext(first).verifyComplete();

        cache.invalidate();

        StepVerifier.create(cache.getGraph()).expectNext(second).verifyComplete();

        verify(builder, times(2)).build();
    }
}
