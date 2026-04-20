package biz.ugur.busroutebackend.transport.application.usecase;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.RouteInAreaInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class FindRoutesInAreaUseCaseTest {

    @InjectMocks
    private FindRoutesInAreaUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void mapsRoutesInAreaToDto() {
        RouteInAreaInfo info = new RouteInAreaInfo(
                "r-1", "29A", "Main", "#FF5722",
                0, 37.96, 58.33, 120.0);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(busRouteRepository.findRoutesIntersectingArea(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Flux.just(info));

        StepVerifier.create(useCase.execute(new FindRoutesInAreaUseCase.Request(37.96, 58.33, 500)))
                .assertNext(dto -> {
                    assertEquals("r-1", dto.getRouteId());
                    assertEquals("29A", dto.getRouteNumber());
                    assertEquals("#FF5722", dto.getRouteColor());
                    assertEquals(37.96, dto.getNearestPoint().getLatitude());
                })
                .verifyComplete();
    }

    @Test
    void returnsEmptyWhenNoRoutesFound() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(busRouteRepository.findRoutesIntersectingArea(anyDouble(), anyDouble(), anyInt()))
                .thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(new FindRoutesInAreaUseCase.Request(37.96, 58.33, 500)))
                .verifyComplete();
    }
}
