package biz.ugur.busroutebackend.transport.infrastructure.services;

import biz.ugur.busroutebackend.transport.domain.repository.RouteStopRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RouteStopsServiceImplTest {

    @Mock
    private RouteStopRepository routeStopRepository;

    @InjectMocks
    private RouteStopsServiceImpl service;

    @Test
    void saveRouteStops_resequencesByDistanceAfterPersistingStops() {
        String routeId = "route-1";
        when(routeStopRepository.deleteExistingStops(routeId)).thenReturn(Mono.empty());
        when(routeStopRepository.insertRouteStop(eq(routeId), anyString(), anyInt(), anyInt())).thenReturn(Mono.empty());
        when(routeStopRepository.resequenceStopsByDistance(routeId)).thenReturn(Mono.empty());

        StepVerifier.create(service.saveRouteStops(routeId, List.of("f1", "f2"), List.of("b1")))
                .verifyComplete();

        verify(routeStopRepository, times(3)).insertRouteStop(eq(routeId), anyString(), anyInt(), anyInt());
        verify(routeStopRepository).resequenceStopsByDistance(routeId);
    }

    @Test
    void saveRouteStops_resequencesEvenWhenOnlyForwardStopsPresent() {
        String routeId = "route-2";
        when(routeStopRepository.deleteExistingStops(routeId)).thenReturn(Mono.empty());
        when(routeStopRepository.insertRouteStop(eq(routeId), anyString(), anyInt(), anyInt())).thenReturn(Mono.empty());
        when(routeStopRepository.resequenceStopsByDistance(routeId)).thenReturn(Mono.empty());

        StepVerifier.create(service.saveRouteStops(routeId, List.of("f1"), List.of()))
                .verifyComplete();

        verify(routeStopRepository).resequenceStopsByDistance(routeId);
    }
}
