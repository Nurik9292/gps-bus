package biz.ugur.busroutebackend.transport.application.usecase.routeswap;

import biz.ugur.busroutebackend.shared.application.CorrelationContextService;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.VerdictCount;
import biz.ugur.busroutebackend.transport.domain.repository.RouteSwapAuditRepository.VerdictRecord;
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

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GetRouteSwapVerdictsUseCaseTest {

    @Mock
    private RouteSwapAuditRepository auditRepository;
    @Mock
    private CorrelationContextService correlationService;

    private GetRouteSwapVerdictsUseCase useCase;

    @BeforeEach
    void setUp() {
        when(correlationService.executeWithCorrelation(any(Mono.class), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        useCase = new GetRouteSwapVerdictsUseCase(auditRepository, correlationService);
    }

    @Test
    void listMapsRecordsToDtosWithSnakeCaseFields() {
        LocalDate date = LocalDate.of(2026, 8, 7);
        when(auditRepository.findVerdicts(eq(date), eq(date), isNull(), anyInt()))
                .thenReturn(Flux.just(new VerdictRecord(7L, "1903 AGH", "veh-1", "100",
                        "SWAP_SUSPECTED", "factual=66@city-001", "66", date, "FIRST",
                        Instant.parse("2026-08-07T05:00:00Z"))));

        StepVerifier.create(useCase.list(date, date, null, 50))
                .assertNext(list -> {
                    assertEquals(1, list.size());
                    assertEquals("1903 AGH", list.get(0).licensePlate());
                    assertEquals("SWAP_SUSPECTED", list.get(0).verdict());
                    assertEquals("100", list.get(0).assignedRouteNumber());
                })
                .verifyComplete();
    }

    @Test
    void listClampsLimitToMaximum() {
        LocalDate date = LocalDate.of(2026, 8, 7);
        when(auditRepository.findVerdicts(any(), any(), any(), anyInt())).thenReturn(Flux.empty());

        StepVerifier.create(useCase.list(date, date, "NO_AXIS_FITS", 100_000))
                .assertNext(list -> assertEquals(0, list.size()))
                .verifyComplete();

        verify(auditRepository).findVerdicts(eq(date), eq(date), eq("NO_AXIS_FITS"), eq(500));
    }

    @Test
    void summaryAggregatesCountsIncludingMissingTypes() {
        when(auditRepository.countVerdictsByType(any(), any())).thenReturn(Flux.just(
                new VerdictCount("SWAP_SUSPECTED", 3), new VerdictCount("NO_AXIS_FITS", 40)));

        StepVerifier.create(useCase.summary())
                .assertNext(summary -> {
                    assertEquals(3, summary.swapSuspected());
                    assertEquals(40, summary.noAxisFits());
                    assertEquals(0, summary.intraFamilyMismatch());
                    assertEquals(43, summary.total());
                })
                .verifyComplete();
    }

    @Test
    void listPropagatesRepositoryError() {
        when(auditRepository.findVerdicts(any(), any(), any(), anyInt()))
                .thenReturn(Flux.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.list(null, null, null, null))
                .expectErrorSatisfies(err -> assertInstanceOf(IllegalStateException.class, err))
                .verify();
    }
}
