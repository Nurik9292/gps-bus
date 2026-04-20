package biz.ugur.busroutebackend.transport.application.usecase.stop;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.shared.application.EventBus;
import biz.ugur.busroutebackend.shared.domain.specification.Specification;
import biz.ugur.busroutebackend.shared.domain.valueObjects.CorrelationId;
import biz.ugur.busroutebackend.transport.application.dto.stop.GetAllStopPaginationQuery;
import biz.ugur.busroutebackend.transport.domain.model.BusStop;
import biz.ugur.busroutebackend.transport.domain.repository.BusStopRepository;
import biz.ugur.busroutebackend.transport.domain.valueobject.StopCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
class GetAllBusStopsUseCaseTest {

    @InjectMocks
    private GetAllBusStopsUseCase useCase;

    @Mock
    private BusStopRepository busStopRepository;

    @Mock
    private CorrelationContextService correlationService;

    @Mock
    private EventBus eventBus;

    private BusStop sampleStop() {
        return BusStop.create(
                "Central", "EN", "Merkez", StopCode.of("AS001"),
                new BigDecimal("37.96"), new BigDecimal("58.33"),
                true, "ashgabat", "admin"
        );
    }

    @Test
    void returnsAllStopsWhenNoFilter() {
        BusStop stop = sampleStop();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.findAll(any(Pageable.class))).thenReturn(Flux.just(stop));
        when(busStopRepository.count()).thenReturn(Mono.just(1L));
        when(busStopRepository.countActiveStops()).thenReturn(Mono.just(1L));

        GetAllStopPaginationQuery q = GetAllStopPaginationQuery.fromParams(
                1, 10, null, null, null, null, null);

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(list -> assertEquals(1, list.getStops().size()))
                .verifyComplete();
    }

    @Test
    void appliesSearchFilter() {
        BusStop stop = sampleStop();

        when(correlationService.getCurrentCorrelationId()).thenReturn(Mono.just(CorrelationId.generate()));
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(busStopRepository.findBySpecification(any(Specification.class), any(Pageable.class)))
                .thenReturn(Flux.just(stop));
        when(busStopRepository.countBySpecification(any(Specification.class))).thenReturn(Mono.just(1L));
        when(busStopRepository.countActiveStops()).thenReturn(Mono.just(5L));

        GetAllStopPaginationQuery q = GetAllStopPaginationQuery.fromParams(
                1, 10, null, null, true, "Central", "ashgabat");

        StepVerifier.create(useCase.execute(Mono.just(q)))
                .assertNext(list -> assertEquals(1, list.getStops().size()))
                .verifyComplete();
    }

    @Test
    void exposesTransportBoundContext() {
        assertEquals("transport", useCase.getBoundContext());
    }
}
