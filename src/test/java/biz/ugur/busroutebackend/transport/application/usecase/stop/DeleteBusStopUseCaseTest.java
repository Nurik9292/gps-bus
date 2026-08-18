package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.application.SecurityContextService;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.domain.exceptions.BusStopInUseException;
import biz.ugur.busroutebackend.transport.domain.model.BusRoute;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusRouteRepository;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusRouteId;
import biz.ugur.busroutebackend.transport.domain.valueobject.BusStopId;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopRouteDetail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeleteBusStopUseCaseTest {

    private static final String STOP_ID = "stop-legacy-42";

    @Mock
    private BusStopRepository busStopRepository;
    @Mock
    private RouteStopRepository routeStopRepository;
    @Mock
    private BusRouteRepository busRouteRepository;
    @Mock
    private SecurityContextService securityContextService;
    @Mock
    private CorrelationContextService correlationService;
    @Mock
    private EventBus eventBus;

    private DeleteBusStopUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new DeleteBusStopUseCase(busStopRepository, routeStopRepository, busRouteRepository,
                securityContextService, correlationService, eventBus);
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(securityContextService.getCurrentUsername()).thenReturn(Mono.just("dispatcher"));
        when(securityContextService.logAudit(anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(busStopRepository.findById(any(BusStopId.class))).thenReturn(Mono.just(stop()));
        when(busStopRepository.deleteById(any(BusStopId.class))).thenReturn(Mono.empty());
        when(routeStopRepository.getStopRoutesDetail(anyString(), anyInt())).thenReturn(Flux.empty());
    }

    private static BusStop stop() {
        return BusStop.builder()
                .id(BusStopId.of(STOP_ID))
                .stopName("Улица Агзыбирлик")
                .latitude(BigDecimal.valueOf(37.9))
                .longitude(BigDecimal.valueOf(58.3))
                .isActive(true)
                .build();
    }

    private static BusRoute route(String id, String number, boolean active) {
        return BusRoute.builder()
                .id(new BusRouteId(id))
                .routeNumber(number)
                .isActive(active)
                .build();
    }

    private static StopRouteDetail usedBy(String routeId, String routeNumber) {
        return new StopRouteDetail(routeId, "маршрут " + routeNumber, routeNumber, 0, 5, 1000);
    }

    @Test
    void stopUsedInActiveRouteIsRejectedWithConflict() {
        when(routeStopRepository.getStopRoutesDetail(STOP_ID, 0))
                .thenReturn(Flux.just(usedBy("route-legacy-94", "7")));
        when(busRouteRepository.findById(new BusRouteId("route-legacy-94")))
                .thenReturn(Mono.just(route("route-legacy-94", "7", true)));

        StepVerifier.create(useCase.execute(Mono.just(STOP_ID)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(BusStopInUseException.class);
                    BusStopInUseException conflict = (BusStopInUseException) error;
                    assertThat(conflict.getErrorCode()).endsWith(".CONFLICT");
                    assertThat(conflict.getRouteNumbers()).containsExactly("7");
                    assertThat(conflict.getMessage()).contains("7");
                })
                .verify();

        verify(busStopRepository, never()).deleteById(any(BusStopId.class));
    }

    @Test
    void conflictListsEveryActiveRoute() {
        when(routeStopRepository.getStopRoutesDetail(STOP_ID, 0))
                .thenReturn(Flux.just(usedBy("r-7", "7"), usedBy("r-110", "110")));
        when(busRouteRepository.findById(new BusRouteId("r-7"))).thenReturn(Mono.just(route("r-7", "7", true)));
        when(busRouteRepository.findById(new BusRouteId("r-110"))).thenReturn(Mono.just(route("r-110", "110", true)));

        StepVerifier.create(useCase.execute(Mono.just(STOP_ID)))
                .expectErrorSatisfies(error ->
                        assertThat(((BusStopInUseException) error).getRouteNumbers())
                                .containsExactlyInAnyOrder("7", "110"))
                .verify();
    }

    @Test
    void stopUsedOnlyInInactiveRouteIsDeleted() {
        when(routeStopRepository.getStopRoutesDetail(STOP_ID, 0))
                .thenReturn(Flux.just(usedBy("r-old", "99")));
        when(busRouteRepository.findById(new BusRouteId("r-old")))
                .thenReturn(Mono.just(route("r-old", "99", false)));

        StepVerifier.create(useCase.execute(Mono.just(STOP_ID))).verifyComplete();

        verify(busStopRepository).deleteById(any(BusStopId.class));
    }

    @Test
    void unusedStopIsDeleted() {
        StepVerifier.create(useCase.execute(Mono.just(STOP_ID))).verifyComplete();

        verify(busStopRepository).deleteById(any(BusStopId.class));
    }
}
