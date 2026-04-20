package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.repository.NearbyRouteQueryRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.NearbyRouteInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetNearbyRoutesUseCaseTest {

    @InjectMocks
    private GetNearbyRoutesUseCase useCase;

    @Mock
    private NearbyRouteQueryRepository repository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Test
    void usesProvidedRadiusWhenGiven() {
        NearbyRouteInfo info = new NearbyRouteInfo("r-1", "29A", "#FF5722");

        when(correlationContextService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(repository.findRoutesNearLocation(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Flux.just(info));

        StepVerifier.create(useCase.execute(Mono.just(
                new GetNearbyRoutesUseCase.Query(37.96, 58.33, 1000))))
                .assertNext(list -> assertEquals(1, list.size()))
                .verifyComplete();

        ArgumentCaptor<Integer> radius = ArgumentCaptor.forClass(Integer.class);
        verify(repository).findRoutesNearLocation(anyDouble(), anyDouble(), radius.capture());
        assertEquals(1000, radius.getValue());
    }

    @Test
    void capsRadiusAtMaximum() {
        when(correlationContextService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(repository.findRoutesNearLocation(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new GetNearbyRoutesUseCase.Query(37.96, 58.33, 10000))))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Integer> radius = ArgumentCaptor.forClass(Integer.class);
        verify(repository).findRoutesNearLocation(anyDouble(), anyDouble(), radius.capture());
        assertEquals(5000, radius.getValue());
    }

    @Test
    void usesDefaultRadiusWhenNull() {
        when(correlationContextService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(repository.findRoutesNearLocation(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new GetNearbyRoutesUseCase.Query(37.96, 58.33))))
                .expectNextCount(1)
                .verifyComplete();

        ArgumentCaptor<Integer> radius = ArgumentCaptor.forClass(Integer.class);
        verify(repository).findRoutesNearLocation(anyDouble(), anyDouble(), radius.capture());
        assertEquals(500, radius.getValue());
    }
}
