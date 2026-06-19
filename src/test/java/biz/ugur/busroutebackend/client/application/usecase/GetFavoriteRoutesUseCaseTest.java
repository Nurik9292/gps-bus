package biz.ugur.busroutebackend.client.application.usecase;

import biz.ugur.busroutebackend.client.domain.model.RouteFavorite;
import biz.ugur.busroutebackend.client.domain.repository.RouteFavoriteRepository;
import biz.ugur.busroutebackend.client.domain.valueobject.ClientId;
import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.transport.application.dto.route.RouteData;
import biz.ugur.busroutebackend.transport.application.usecase.route.GetRouteByIdUseCase;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFavoriteRoutesUseCaseTest {

    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private RouteFavoriteRepository routeFavoriteRepository;
    @Mock
    private GetRouteByIdUseCase getRouteByIdUseCase;
    @InjectMocks
    private GetFavoriteRoutesUseCase useCase;

    @SuppressWarnings("unchecked")
    private void passthroughCorrelation() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void execute_loadsFavoriteRoutes_andSkipsUnloadable() {
        passthroughCorrelation();
        when(routeFavoriteRepository.findByClientId(any())).thenReturn(Flux.just(
                RouteFavorite.create(ClientId.of("c1"), BusRouteId.of("r1")),
                RouteFavorite.create(ClientId.of("c1"), BusRouteId.of("r2"))));
        RouteData loaded = mock(RouteData.class);
        when(getRouteByIdUseCase.execute(any()))
                .thenReturn(Mono.just(loaded))
                .thenReturn(Mono.error(new RuntimeException("route gone")));

        StepVerifier.create(useCase.execute("c1"))
                .assertNext(routes -> assertThat(routes).containsExactly(loaded))
                .verifyComplete();
    }

    @Test
    void execute_emptyWhenNoFavorites() {
        passthroughCorrelation();
        when(routeFavoriteRepository.findByClientId(any())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.execute("c1"))
                .assertNext(routes -> assertThat(routes).isEmpty())
                .verifyComplete();
    }
}
