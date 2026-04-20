package biz.ugur.busroutebackend.transport.application.usecase.route;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.mapper.RouteDataMapper;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class FindRouteByIdUseCaseTest {

    @InjectMocks
    private FindRouteByIdUseCase useCase;

    @Mock
    private BusRouteRepository busRouteRepository;

    @Mock
    private RouteDataMapper routeDataMapper;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    @Test
    void returnsRouteDataWhenFound() {
        BusRoute route = BusRoute.create("29A", "Main", "", "", "#FF5722", "ashgabat", 30);
        RouteData data = mock(RouteData.class);

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(route.getId())).thenReturn(Mono.just(route));
        when(routeDataMapper.toRouteData(route)).thenReturn(Mono.just(data));

        StepVerifier.create(useCase.execute(route.getId().getValue()))
                .expectNext(data)
                .verifyComplete();
    }

    @Test
    void completesEmptyWhenNotFound() {
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busRouteRepository.findById(any(BusRouteId.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.execute(BusRouteId.generate().getValue()))
                .verifyComplete();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
