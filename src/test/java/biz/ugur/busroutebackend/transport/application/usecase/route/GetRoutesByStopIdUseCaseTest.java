package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopRouteDetail;
import biz.ugur.busroutebackend.transport.infrastructure.persistence.repository.R2dbcRouteStopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetRoutesByStopIdUseCaseTest {

    @InjectMocks
    private GetRoutesByStopIdUseCase useCase;

    @Mock
    private R2dbcRouteStopRepository repository;

    @Mock
    private CorrelationContextService correlationContextService;

    @Test
    void returnsRoutesForStop() {
        StopRouteDetail detail = new StopRouteDetail(
                "r-1", "Main Route", "29A", 0, 30, 1500);

        when(correlationContextService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(repository.getStopRoutesDetail(anyString(), anyInt())).thenReturn(Flux.just(detail));

        StepVerifier.create(useCase.execute(Mono.just(
                new GetRoutesByStopIdUseCase.Query("stop-1", 0))))
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("r-1", list.get(0).routeId());
                    assertEquals("29A", list.get(0).routeNumber());
                })
                .verifyComplete();
    }

    @Test
    void returnsEmptyListWhenNoRoutes() {
        when(correlationContextService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationContextService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(repository.getStopRoutesDetail(anyString(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute(Mono.just(
                new GetRoutesByStopIdUseCase.Query("stop-x", 1))))
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();
    }
}
